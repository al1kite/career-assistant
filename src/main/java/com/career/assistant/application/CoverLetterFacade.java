package com.career.assistant.application;

import com.career.assistant.api.dto.ReviewResponse;
import com.career.assistant.application.review.ReviewAgent;
import com.career.assistant.application.review.ReviewResult;
import com.career.assistant.domain.coverletter.CoverLetter;
import com.career.assistant.domain.coverletter.CoverLetterRepository;
import com.career.assistant.domain.experience.UserExperience;
import com.career.assistant.domain.experience.UserExperienceRepository;
import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.domain.jobposting.JobPostingRepository;
import com.career.assistant.infrastructure.ai.AiPort;
import com.career.assistant.infrastructure.ai.AiRouter;
import com.career.assistant.infrastructure.crawling.CrawledJobInfo;
import com.career.assistant.infrastructure.crawling.EssayQuestion;
import com.career.assistant.infrastructure.crawling.JsoupCrawler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.career.assistant.infrastructure.crawling.EmploymentOption;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverLetterFacade {

    private static final int MAX_ITERATIONS = 2;
    private static final int MIN_ITERATIONS = 1;
    private static final String QUALITY_GRADE = "A";

    private final JobPostingRepository jobPostingRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final ExperienceEmbeddingService experienceEmbeddingService;
    private final JsoupCrawler jsoupCrawler;
    private final CompanyClassifier companyClassifier;
    private final CompanyAnalyzer companyAnalyzer;
    private final CoverLetterPromptBuilder promptBuilder;
    private final CoverLetterStrategyPlanner strategyPlanner;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;
    private final ReviewAgent reviewAgent;

    public static Map<Integer, CoverLetter> extractLatestByQuestion(List<CoverLetter> letters) {
        Map<Integer, CoverLetter> latest = new LinkedHashMap<>();
        for (CoverLetter cl : letters) {
            int qIdx = cl.getQuestionIndex() != null ? cl.getQuestionIndex() : 0;
            CoverLetter existing = latest.get(qIdx);
            if (existing == null || cl.getVersion() > existing.getVersion()) {
                latest.put(qIdx, cl);
            }
        }
        return latest;
    }

    @Transactional
    public ReviewResponse reviewUserDraft(Long jobPostingId, String content, Integer questionIndex) {
        JobPosting jp = jobPostingRepository.findById(jobPostingId)
            .orElseThrow(() -> new IllegalArgumentException("공고를 찾을 수 없습니다: " + jobPostingId));

        int qIdx = questionIndex != null ? questionIndex : 0;

        // 기존 최신 버전 조회 → 새 버전 번호 계산
        int nextVersion = coverLetterRepository
            .findTopByJobPostingIdAndQuestionIndexOrderByVersionDesc(jobPostingId, qIdx)
            .map(cl -> cl.getVersion() + 1)
            .orElse(1);

        // 문항 텍스트 가져오기
        String questionText = coverLetterRepository
            .findTopByJobPostingIdAndQuestionIndexOrderByVersionDesc(jobPostingId, qIdx)
            .map(CoverLetter::getQuestionText)
            .orElse(null);

        // charLimit 계산
        Map<Integer, Integer> charLimitByQuestion = deserializeCharLimits(jp);
        int charLimit = charLimitByQuestion.getOrDefault(qIdx, 1000);

        // 사용자 수정본으로 새 버전 저장
        CoverLetter newVersion = CoverLetter.ofVersion(
            jp, "user-edit", content, nextVersion, qIdx, questionText
        );
        coverLetterRepository.save(newVersion);

        // 리뷰 실행
        List<UserExperience> experiences = retrieveExperiencesOrFallback(jp, questionText);
        ReviewResult review = reviewAgent.review(content, jp, questionText, 1, experiences, charLimit);

        // 리뷰 결과 저장
        newVersion.addReview(review.rawJson(), review.totalScore());
        coverLetterRepository.save(newVersion);

        log.info("[검토] 사용자 수정본 검토 완료 - 회사: {}, 문항: {}, v{}, 점수: {}",
            jp.getCompanyName(), qIdx, nextVersion, review.totalScore());

        // ReviewResponse로 변환
        Map<String, Integer> scoreMap = new LinkedHashMap<>();
        scoreMap.put("answerRelevance", review.scores().answerRelevance());
        scoreMap.put("jobFit", review.scores().jobFit());
        scoreMap.put("orgFit", review.scores().orgFit());
        scoreMap.put("specificity", review.scores().specificity());
        scoreMap.put("authenticity", review.scores().authenticity());
        scoreMap.put("aiDetectionRisk", review.scores().aiDetectionRisk());
        scoreMap.put("logicalStructure", review.scores().logicalStructure());
        scoreMap.put("keywordUsage", review.scores().keywordUsage());
        scoreMap.put("experienceConsistency", review.scores().experienceConsistency());

        return new ReviewResponse(
            review.totalScore(), review.grade(), review.overallComment(),
            scoreMap, review.violations(), review.improvements(),
            newVersion.getId(), nextVersion
        );
    }

    @Transactional
    public List<CoverLetter> improveExisting(Long jobPostingId) {
        return improveExisting(jobPostingId, null);
    }

    @Transactional
    public List<CoverLetter> improveExisting(Long jobPostingId, String userMessage) {
        JobPosting jp = jobPostingRepository.findById(jobPostingId)
            .orElseThrow(() -> new IllegalArgumentException("공고를 찾을 수 없습니다: " + jobPostingId));
        List<CoverLetter> allLetters = coverLetterRepository.findByJobPostingId(jobPostingId);
        if (allLetters.isEmpty()) {
            throw new IllegalStateException("개선할 자소서가 없습니다");
        }

        Map<Integer, CoverLetter> latestByQuestion = extractLatestByQuestion(allLetters);
        AiPort ai = aiRouter.route(jp.getCompanyType());
        String jobContext = promptBuilder.buildJobContext(jp);
        List<CoverLetter> results = new ArrayList<>();

        // essayQuestionsJson에서 문항별 charLimit 매핑
        Map<Integer, Integer> charLimitByQuestion = deserializeCharLimits(jp);

        log.info("[개선] 기존 자소서 추가 개선 시작 - 회사: {}, 문항 {}개", jp.getCompanyName(), latestByQuestion.size());

        Set<Long> usedPrimaryIds = new LinkedHashSet<>();
        List<CoverLetter> latestLetters = latestByQuestion.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .toList();

        for (CoverLetter latest : latestLetters) {
            int qIdx = latest.getQuestionIndex() != null ? latest.getQuestionIndex() : 0;
            int charLimit = charLimitByQuestion.getOrDefault(qIdx, 1000);
            List<UserExperience> experiences = retrieveExperiencesOrFallback(
                jp, latest.getQuestionText(), usedPrimaryIds);
            UserExperience primary = getPrimaryExperience(experiences);
            if (primary != null) {
                usedPrimaryIds.add(primary.getId());
            }

            log.info("[RAG] 문항 {} 검색된 경험 {}건, 주력 경험 ID: {}",
                qIdx, experiences.size(), primary != null ? primary.getId() : "없음");
            CoverLetter improved = generateWithReviewLoop(
                latest, jp, experiences, ai, jobContext, latest.getQuestionText(), null, charLimit, userMessage);
            results.add(improved);

            log.info("[개선] 문항 {} 개선 완료 - v{} → v{}, 점수: {}",
                latest.getQuestionIndex(), latest.getVersion(), improved.getVersion(), improved.getReviewScore());
        }

        log.info("[개선] 추가 개선 완료 - 회사: {}, 문항 {}개", jp.getCompanyName(), results.size());
        return results;
    }

    @Transactional
    public List<CoverLetter> generateFromUrl(String url) {
        return generateFromUrl(url, null);
    }

    @Transactional
    public List<CoverLetter> generateFromUrl(String url, Integer employmentId) {
        if (jobPostingRepository.existsByUrl(url)) {
            log.info("이미 처리된 공고: {}", url);
            JobPosting existing = jobPostingRepository.findByUrl(url).orElseThrow();

            // employmentId가 지정된 경우 → 해당 직무로 다시 크롤링/생성
            if (employmentId != null) {
                log.info("employmentId={} 지정 — 해당 직무로 재생성: {}", employmentId, url);
                return crawlAndGenerate(existing, employmentId);
            }

            // 자동수집으로 FETCHED 상태인 공고 → 크롤링/분석부터 다시 수행
            if (existing.needsCrawling()) {
                log.info("수집만 된 공고 — 크롤링/분석 시작: {}", url);
                return crawlAndGenerate(existing, null);
            }

            List<CoverLetter> existingLetters = coverLetterRepository.findByJobPostingId(existing.getId());
            if (!existingLetters.isEmpty()) {
                return existingLetters;
            }
            return generateCoverLetters(existing, List.of());
        }

        JobPosting jobPosting = JobPosting.from(url);
        jobPostingRepository.save(jobPosting);

        return crawlAndGenerate(jobPosting, employmentId);
    }

    private List<CoverLetter> crawlAndGenerate(JobPosting jobPosting) {
        return crawlAndGenerate(jobPosting, null);
    }

    private List<CoverLetter> crawlAndGenerate(JobPosting jobPosting, Integer employmentId) {
        try {
            // 1단계: 크롤링
            CrawledJobInfo crawledInfo = jsoupCrawler.crawl(jobPosting.getUrl());

            // 1-1단계: 다중 직무 공고 — 사용자 선택 우선, 자동 매칭 폴백
            if (crawledInfo.employmentOptions().size() > 1) {
                EmploymentOption best;
                if (employmentId != null) {
                    // 사용자가 직접 선택한 employment 사용
                    best = crawledInfo.employmentOptions().stream()
                        .filter(o -> o.id() == employmentId)
                        .findFirst()
                        .orElse(null);
                    if (best != null) {
                        log.info("[매칭] 사용자 직접 선택 직무: {} (id={})", best.field(), best.id());
                    } else {
                        log.warn("[매칭] 사용자 지정 employmentId={} 를 찾을 수 없음 — 자동 매칭 폴백", employmentId);
                        List<UserExperience> experiences = userExperienceRepository.findAll();
                        best = findBestEmployment(crawledInfo.employmentOptions(), experiences);
                    }
                } else {
                    // 기존 자동 매칭
                    List<UserExperience> experiences = userExperienceRepository.findAll();
                    best = findBestEmployment(crawledInfo.employmentOptions(), experiences);
                }
                if (best != null && best.id() != crawledInfo.employmentOptions().get(0).id()) {
                    log.info("[매칭] 사용자 경험 기반 직무 자동 선택: {} (id={}) ← 기본: {} (id={})",
                        best.field(), best.id(),
                        crawledInfo.employmentOptions().get(0).field(),
                        crawledInfo.employmentOptions().get(0).id());
                    try {
                        CrawledJobInfo refined = jsoupCrawler.fetchForEmployment(best.id());
                        crawledInfo = CrawledJobInfo.of(
                            crawledInfo.companyName(),
                            mergeJobDescriptions(crawledInfo.jobDescription(), refined.jobDescription()),
                            crawledInfo.requirements(),
                            crawledInfo.deadline(),
                            crawledInfo.active(),
                            refined.essayQuestions().isEmpty()
                                ? crawledInfo.essayQuestions() : refined.essayQuestions(),
                            crawledInfo.employmentOptions()
                        );
                    } catch (Exception e) {
                        log.warn("[매칭] 선택된 employment 재조회 실패 — employment 옵션 정보로 보강: {}", e.getMessage());
                        String fallbackJd = buildFallbackEmploymentJd(best);
                        crawledInfo = CrawledJobInfo.of(
                            crawledInfo.companyName(),
                            mergeJobDescriptions(crawledInfo.jobDescription(), fallbackJd),
                            crawledInfo.requirements(),
                            crawledInfo.deadline(),
                            crawledInfo.active(),
                            crawledInfo.essayQuestions(),
                            crawledInfo.employmentOptions()
                        );
                    }
                } else if (best != null) {
                    log.info("[매칭] 기본 선택 직무가 최적: {} (id={})", best.field(), best.id());
                }
            }

            List<EssayQuestion> essayQuestions = crawledInfo.essayQuestions();

            String essayQuestionsJson = serializeQuestions(essayQuestions);
            LocalDate deadline = parseDeadline(crawledInfo.deadline());

            jobPosting.updateCrawledInfo(
                crawledInfo.companyName(),
                crawledInfo.jobDescription(),
                crawledInfo.requirements(),
                essayQuestionsJson,
                deadline
            );

            // 2단계: 회사 유형 분류
            var companyType = companyClassifier.classify(
                crawledInfo.companyName(), crawledInfo.jobDescription());
            jobPosting.classify(companyType);

            // 3단계: AI 회사 심층 분석
            try {
                log.info("[분석] 회사 분석 시작 - {}", crawledInfo.companyName());
                String analysisJson = companyAnalyzer.analyze(jobPosting, essayQuestions);
                if (analysisJson != null) {
                    jobPosting.updateCompanyAnalysis(analysisJson);
                    log.info("[분석] 회사 분석 완료 - {}", crawledInfo.companyName());
                } else {
                    log.warn("[분석] 회사 분석 결과 없음 - 분석 없이 진행합니다.");
                }
            } catch (Exception e) {
                log.warn("[분석] 회사 분석 실패 - 분석 없이 진행합니다: {}", e.getMessage());
            }

            // 4단계: 자소서 생성 (에이전트 루프)
            return generateCoverLetters(jobPosting, essayQuestions);

        } catch (Exception e) {
            jobPosting.markFailed();
            log.error("자소서 생성 실패: {}", jobPosting.getUrl(), e);
            throw e;
        }
    }

    private List<CoverLetter> generateCoverLetters(JobPosting jobPosting, List<EssayQuestion> essayQuestions) {
        AiPort ai = aiRouter.route(jobPosting.getCompanyType());
        String jobContext = promptBuilder.buildJobContext(jobPosting);

        if (!userExperienceRepository.existsAny()) {
            log.warn("[경고] 등록된 경험(UserExperience)이 0건입니다. 자소서 품질이 크게 저하될 수 있습니다. " +
                "경험 데이터를 먼저 등록해주세요.");
        }

        jobPosting.markReviewing();

        if (essayQuestions == null || essayQuestions.isEmpty()) {
            List<UserExperience> experiences = retrieveExperiencesOrFallback(jobPosting, null);
            log.info("[RAG] 검색된 경험 {}건 (단일 자소서)", experiences.size());

            String prompt = promptBuilder.build(jobPosting, experiences, 1000);
            String content = enforceCharLimit(ai.generateWithContext(jobContext, prompt), 1000);

            CoverLetter coverLetter = CoverLetter.of(jobPosting, ai.getModelName(), content);
            coverLetterRepository.save(coverLetter);

            CoverLetter finalLetter = generateWithReviewLoop(
                coverLetter, jobPosting, experiences, ai, jobContext, null, null, 1000, null
            );

            jobPosting.markFinalized();
            log.info("[에이전트] 자소서 완료 (단일) - 회사: {}, 최종 v{}, 점수: {}",
                jobPosting.getCompanyName(), finalLetter.getVersion(), finalLetter.getReviewScore());
            return List.of(finalLetter);
        }

        // 전략 수립 (문항 2개 이상일 때)
        String masterPlan = null;
        try {
            List<UserExperience> allExperiences = userExperienceRepository.findAll();
            masterPlan = strategyPlanner.planStrategy(jobPosting, essayQuestions, allExperiences);
        } catch (Exception e) {
            log.warn("[전략] 전략 수립 중 오류 — 개별 생성 방식으로 진행: {}", e.getMessage());
        }

        List<CoverLetter> finalLetters = new ArrayList<>();
        Set<Long> usedPrimaryIds = new LinkedHashSet<>();
        for (EssayQuestion question : essayQuestions) {
            List<UserExperience> experiences = retrieveExperiencesOrFallback(
                jobPosting, question.questionText(), usedPrimaryIds);
            UserExperience primary = getPrimaryExperience(experiences);
            List<UserExperience> secondary = getSecondaryExperiences(experiences);

            if (primary != null) {
                usedPrimaryIds.add(primary.getId());
            }

            log.info("[RAG] 문항 {} 검색된 경험 {}건, 주력 경험 ID: {}",
                question.number(), experiences.size(), primary != null ? primary.getId() : "없음");

            String prompt = promptBuilder.buildForQuestion(jobPosting, primary, secondary, question, masterPlan);
            int charLimit = question.charLimit() > 0 ? question.charLimit() : 1000;
            String content = enforceCharLimit(ai.generateWithContext(jobContext, prompt), charLimit);

            CoverLetter coverLetter = CoverLetter.of(
                jobPosting, ai.getModelName(), content,
                question.number(), question.questionText()
            );
            coverLetterRepository.save(coverLetter);

            CoverLetter finalLetter = generateWithReviewLoop(
                coverLetter, jobPosting, experiences, ai, jobContext,
                question.questionText(), question, charLimit, null
            );
            finalLetters.add(finalLetter);

            log.info("[에이전트] 문항 {} 완료 - 회사: {}, 최종 v{}, 점수: {}",
                question.number(), jobPosting.getCompanyName(),
                finalLetter.getVersion(), finalLetter.getReviewScore());
        }

        jobPosting.markFinalized();
        log.info("[에이전트] 자소서 전체 완료 (문항 {}개) - 회사: {}, 모델: {}",
            finalLetters.size(), jobPosting.getCompanyName(), ai.getModelName());
        return finalLetters;
    }

    private CoverLetter generateWithReviewLoop(CoverLetter currentLetter, JobPosting jobPosting,
                                                List<UserExperience> experiences, AiPort ai,
                                                String jobContext,
                                                String questionText, EssayQuestion essayQuestion,
                                                int charLimit, String userMessage) {
        String currentDraft = currentLetter.getContent();
        CoverLetter latest = currentLetter;
        CoverLetter bestLetter = currentLetter;
        int bestScore = -1;

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            log.info("[에이전트] v{} → {}차 검토 시작 (문항: {})",
                latest.getVersion(), iteration,
                questionText != null ? questionText.substring(0, Math.min(20, questionText.length())) : "단일");

            // 검토
            ReviewResult review;
            try {
                review = reviewAgent.review(currentDraft, jobPosting, questionText, iteration, experiences, charLimit);
            } catch (Exception e) {
                log.error("[에이전트] {}차 검토 중 API 오류: {}", iteration, e.getMessage());
                if (iteration < MIN_ITERATIONS) {
                    log.warn("[에이전트] 최소 반복 미달 ({}/{}) — 다음 반복에서 재시도", iteration, MIN_ITERATIONS);
                    continue;
                }
                break;
            }

            // 검토 결과 저장
            latest.addReview(review.rawJson(), review.totalScore());
            coverLetterRepository.save(latest);

            log.info("[에이전트] {}차 검토 결과 - 점수: {}점({}등급), violations: {}개, improvements: {}개",
                iteration, review.totalScore(), review.grade(),
                review.violations().size(), review.improvements().size());

            // 최고 점수 버전 추적
            if (review.totalScore() > bestScore) {
                bestScore = review.totalScore();
                bestLetter = latest;
            } else {
                log.warn("[에이전트] 점수 하락 감지 ({}점 → {}점) — 최고 버전: v{}({}점)",
                    bestScore, review.totalScore(), bestLetter.getVersion(), bestScore);
            }

            // 품질 등급 통과 (최소 반복 횟수 이후에만 적용)
            if (iteration >= MIN_ITERATIONS && passesQualityGrade(review.grade())) {
                log.info("[에이전트] 품질 기준 통과! ({}등급, {}회 반복)", review.grade(), iteration);
                break;
            }
            if (iteration < MIN_ITERATIONS && passesQualityGrade(review.grade())) {
                log.info("[에이전트] {}등급 도달했으나 최소 반복 미달 ({}/{}) — 추가 개선 진행",
                    review.grade(), iteration, MIN_ITERATIONS);
            }

            // 마지막 반복이면 더 이상 개선하지 않음
            if (iteration == MAX_ITERATIONS) {
                log.info("[에이전트] 최대 반복 횟수 도달 ({}회), 최고 버전(v{}, {}점)으로 확정",
                    MAX_ITERATIONS, bestLetter.getVersion(), bestScore);
                break;
            }

            // 개선 프롬프트 생성 및 AI 호출
            log.info("[에이전트] 개선 프롬프트 생성 → v{} 작성 중...", latest.getVersion() + 1);
            try {
                String targetedStrategy = buildTargetedStrategy(review);
                log.info("[에이전트] 타겟 전략: {}", targetedStrategy.replace("\n", " | "));
                String improvementPrompt = promptBuilder.buildImprovementPrompt(
                    jobPosting, experiences, questionText, currentDraft, review.rawJson(), iteration, targetedStrategy,
                    charLimit, userMessage
                );
                String improvedContent = enforceCharLimit(
                    jobContext != null ? ai.generateWithContext(jobContext, improvementPrompt) : ai.generate(improvementPrompt),
                    charLimit);

                // 새 버전 저장
                CoverLetter newVersion = CoverLetter.ofVersion(
                    jobPosting, ai.getModelName(), improvedContent,
                    latest.getVersion() + 1,
                    latest.getQuestionIndex(), latest.getQuestionText()
                );
                coverLetterRepository.save(newVersion);

                currentDraft = improvedContent;
                latest = newVersion;
            } catch (Exception e) {
                log.error("[에이전트] {}차 개선 중 API 오류: {}", iteration, e.getMessage());
                if (iteration < MIN_ITERATIONS) {
                    log.warn("[에이전트] 최소 반복 미달 ({}/{}) — 동일 초안으로 다음 검토 재시도", iteration, MIN_ITERATIONS);
                    continue;
                }
                break;
            }
        }

        // DB 최신 버전과 최고 점수 버전 일치 보장
        if (bestLetter != latest && bestScore > 0) {
            log.info("[에이전트] 최고 점수 버전(v{}, {}점)을 최종 버전(v{})으로 확정",
                bestLetter.getVersion(), bestScore, latest.getVersion() + 1);
            CoverLetter finalVersion = CoverLetter.ofVersion(
                jobPosting, ai.getModelName(), bestLetter.getContent(),
                latest.getVersion() + 1,
                bestLetter.getQuestionIndex(), bestLetter.getQuestionText()
            );
            finalVersion.addReview(bestLetter.getFeedback(), bestScore);
            coverLetterRepository.save(finalVersion);
            return finalVersion;
        }

        return bestLetter;
    }

    private boolean passesQualityGrade(String grade) {
        return QUALITY_GRADE.equals(grade) || "S".equals(grade);
    }

    String buildTargetedStrategy(ReviewResult review) {
        var weakest = review.getWeakestDimensions(3);
        StringBuilder sb = new StringBuilder();
        sb.append("[가장 시급한 개선 영역]\n");
        sb.append("아래 3개 항목이 가장 낮은 점수를 받았습니다. 이 영역을 집중적으로 개선하세요.\n\n");

        for (var dim : weakest) {
            String field = (String) dim.get("field");
            String name = (String) dim.get("name");
            int score = (int) dim.get("score");
            String advice = getFixAdvice(field, score);
            sb.append("▸ ").append(name).append(" (").append(score).append("점): ").append(advice).append("\n");
        }

        return sb.toString();
    }

    String getFixAdvice(String field, int score) {
        return switch (field) {
            case "answerRelevance" -> "[기준5] 첫 문장부터 질문 키워드에 직접 응답하세요. 질문이 묻는 것에 정면으로 답하고, 문항 하위 질문을 빠짐없이 다루세요.";
            case "jobFit" -> "[기준1] 채용공고 자격요건의 기술 키워드를 본인 경험과 직접 연결하세요. 구체적 프로젝트와 성과를 매칭하세요.";
            case "orgFit" -> "[기준2,8] 회사 분석의 핵심 가치/문화를 구체적으로 언급하세요. 기업 고유명사 2개 이상 필수. 이 회사가 아니면 안 되는 절실함을 보여주세요.";
            case "specificity" -> "[기준4] '많은 개선'→'응답시간 2.3초→0.4초'로 교체하세요. 숫자, 프로젝트명, KPI를 반드시 포함하세요.";
            case "authenticity" -> "[기준3,8] 이 지원자만 쓸 수 있는 구체적 장면을 추가하세요. 진부한 표현을 제거하고, 날짜/시간/감정 등 생생한 디테일을 녹이세요.";
            case "aiDetectionRisk" -> "[기준3] 어미 반복을 깨고, 구어체 전환어('솔직히', '돌이켜보면')를 추가하고, 감정 표현을 넣으세요.";
            case "logicalStructure" -> "[기준6] 기승전결 순서를 점검하세요. 불필요한 수식어를 삭제하고, 단락 간 논리 연결이 자연스러운지 확인하세요.";
            case "keywordUsage" -> "[기준1] 채용공고의 핵심 키워드 3~5개를 추출하여 문맥에 맞게 자연스럽게 포함하세요.";
            case "experienceConsistency" -> "[기준4] 제공된 경험 목록에 없는 프로젝트나 경력을 삭제하세요. 실제 경험만 정확히 인용하세요.";
            default -> "해당 항목의 점수를 높이기 위해 구체성과 관련성을 강화하세요.";
        };
    }

    String enforceCharLimit(String content, int charLimit) {
        if (charLimit <= 0 || content == null) return content;
        if (content.length() <= charLimit) return content;

        // 120% 초과 시 문장 단위 트리밍
        double ratio = (double) content.length() / charLimit;
        if (ratio > 1.2) {
            log.warn("[글자수] 제한 대비 {}% 초과 ({}/{}자) — 문장 단위 트리밍 적용",
                Math.round((ratio - 1) * 100), content.length(), charLimit);
        } else {
            log.info("[글자수] 제한 초과 ({}/{}자) — 문장 단위 트리밍 적용", content.length(), charLimit);
        }

        // 문장 단위로 분리하여 charLimit 이내로 자르기
        String[] sentences = content.split("(?<=[.!?。])\\s*");
        StringBuilder trimmed = new StringBuilder();
        for (String sentence : sentences) {
            if (trimmed.length() + sentence.length() > charLimit) {
                break;
            }
            trimmed.append(sentence);
        }

        // 문장 단위로 잘라도 빈 문자열이면 강제 절삭
        if (trimmed.isEmpty()) {
            return content.substring(0, charLimit);
        }

        log.info("[글자수] 트리밍 결과: {}자 → {}자 (제한: {}자)", content.length(), trimmed.length(), charLimit);
        return trimmed.toString();
    }

    private Map<Integer, Integer> deserializeCharLimits(JobPosting jp) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        String json = jp.getEssayQuestionsJson();
        if (json == null || json.isBlank()) return map;
        try {
            List<EssayQuestion> questions = objectMapper.readValue(json, new TypeReference<>() {});
            for (EssayQuestion q : questions) {
                map.put(q.number(), q.charLimit() > 0 ? q.charLimit() : 1000);
            }
        } catch (Exception e) {
            log.warn("essayQuestionsJson 역직렬화 실패: {}", e.getMessage());
        }
        return map;
    }

    private List<UserExperience> retrieveExperiencesOrFallback(JobPosting jobPosting, String questionText) {
        return retrieveExperiencesOrFallback(jobPosting, questionText, Set.of());
    }

    private List<UserExperience> retrieveExperiencesOrFallback(
        JobPosting jobPosting, String questionText, Set<Long> excludeIds
    ) {
        try {
            String query = buildRetrievalQuery(jobPosting, questionText);
            return experienceEmbeddingService.retrieveRelevant(query, 5, excludeIds);
        } catch (Exception e) {
            log.warn("[RAG] 벡터 검색 실패 — findAll 폴백: {}", e.getMessage());
            List<UserExperience> all = userExperienceRepository.findAll();
            if (excludeIds.isEmpty()) return all;
            List<UserExperience> filtered = all.stream()
                .filter(exp -> !excludeIds.contains(exp.getId()))
                .toList();
            return filtered.isEmpty() ? all : filtered;
        }
    }

    private UserExperience getPrimaryExperience(List<UserExperience> experiences) {
        return experiences.isEmpty() ? null : experiences.get(0);
    }

    private List<UserExperience> getSecondaryExperiences(List<UserExperience> experiences) {
        return experiences.size() <= 1 ? List.of() : experiences.subList(1, experiences.size());
    }

    private String buildRetrievalQuery(JobPosting jobPosting, String questionText) {
        // 문항 없음 (단일 자소서): 기존 로직 유지
        if (questionText == null || questionText.isBlank()) {
            StringBuilder sb = new StringBuilder();
            if (jobPosting.getCompanyName() != null) {
                sb.append(jobPosting.getCompanyName()).append(" ");
            }
            if (jobPosting.getJobDescription() != null) {
                sb.append(jobPosting.getJobDescription(), 0,
                    Math.min(500, jobPosting.getJobDescription().length())).append(" ");
            }
            if (jobPosting.getRequirements() != null) {
                sb.append(jobPosting.getRequirements(), 0,
                    Math.min(300, jobPosting.getRequirements().length())).append(" ");
            }
            return sb.toString().trim();
        }

        // 문항 있음: 문항 중심 쿼리 (문항텍스트 비중 ~40%+)
        StringBuilder sb = new StringBuilder();
        sb.append(questionText).append(" ");
        sb.append(retrievalKeywords(questionText)).append(" ");
        if (jobPosting.getCompanyName() != null) {
            sb.append(jobPosting.getCompanyName()).append(" ");
        }
        if (jobPosting.getJobDescription() != null) {
            sb.append(jobPosting.getJobDescription(), 0,
                Math.min(100, jobPosting.getJobDescription().length())).append(" ");
        }
        if (jobPosting.getRequirements() != null) {
            sb.append(jobPosting.getRequirements(), 0,
                Math.min(50, jobPosting.getRequirements().length()));
        }
        return sb.toString().trim();
    }

    private String classifyForRetrieval(String questionText) {
        String q = questionText.toLowerCase();
        if (q.contains("포트폴리오") || q.contains("github") || q.contains("깃허브")
            || q.contains("블로그") || q.contains("url") || q.contains("링크")
            || q.contains("첨부") || q.contains("파일") || q.contains("노션")) return "포트폴리오";
        if (q.contains("지원동기") || q.contains("지원 동기")
            || (q.contains("왜") && q.contains("회사"))
            || (q.contains("선택") && q.contains("이유"))
            || q.contains("지원하게 된")) return "지원동기";
        if (q.contains("역량") || q.contains("강점") || q.contains("능력") || q.contains("직무")
            || q.contains("전문") || q.contains("기술") || q.contains("경쟁력")) return "핵심역량";
        if (q.contains("문제") || q.contains("해결") || q.contains("도전") || q.contains("어려움")
            || q.contains("극복") || q.contains("실패") || q.contains("위기")) return "문제해결";
        if (q.contains("협업") || q.contains("리더") || q.contains("팀") || q.contains("소통")
            || q.contains("갈등") || q.contains("설득") || q.contains("커뮤니케이션") || q.contains("조직")) return "협업리더십";
        if ((q.contains("입사") && q.contains("후")) || q.contains("포부") || q.contains("계획")
            || q.contains("비전") || q.contains("목표") || q.contains("각오")) return "입사후포부";
        if (q.contains("장단점") || q.contains("장점과 단점") || q.contains("장점") && q.contains("단점")
            || q.contains("약점") || q.contains("보완")) return "장단점";
        if (q.contains("성장") || q.contains("가치") || q.contains("인생") || q.contains("신념")
            || q.contains("좌우명") || q.contains("성격") || q.contains("본인 소개")) return "성장과정";
        return "일반";
    }

    private String retrievalKeywords(String questionText) {
        return switch (classifyForRetrieval(questionText)) {
            case "포트폴리오" -> "프로젝트 포트폴리오 GitHub 블로그";
            case "지원동기" -> "동기 관심 계기 선택 이유";
            case "핵심역량" -> "역량 기술 성과 전문성";
            case "문제해결" -> "문제 해결 도전 극복 장애";
            case "협업리더십" -> "협업 팀 리더 소통 갈등 조율";
            case "입사후포부" -> "목표 계획 비전 성장 기여";
            case "장단점" -> "장점 단점 강점 약점 보완 개선";
            case "성장과정" -> "가치관 경험 전환점 성장";
            default -> "";
        };
    }

    EmploymentOption findBestEmployment(List<EmploymentOption> options, List<UserExperience> experiences) {
        if (options.isEmpty()) return null;
        if (experiences.isEmpty()) return options.get(0);

        // 모든 경험에서 skills 추출
        Set<String> skillKeywords = new LinkedHashSet<>();
        for (UserExperience exp : experiences) {
            if (exp.getSkills() != null && !exp.getSkills().isBlank()) {
                for (String skill : exp.getSkills().split("[,/·]+")) {
                    String trimmed = skill.trim().toLowerCase();
                    if (!trimmed.isBlank() && trimmed.length() >= 2) {
                        skillKeywords.add(trimmed);
                    }
                }
            }
        }
        if (skillKeywords.isEmpty()) return options.get(0);

        // 역할 추론
        String combined = String.join(" ", skillKeywords);
        String inferredDomain = inferDomain(combined);
        log.info("[매칭] 사용자 기술스택 기반 추론 도메인: {}, 키워드: {}", inferredDomain, skillKeywords);

        // 각 employment의 field와 매칭
        EmploymentOption best = null;
        int bestScore = -1;

        for (EmploymentOption option : options) {
            int score = calcMatchScore(option.field(), option.title(), option.department(), inferredDomain);
            log.debug("[매칭] employment id={} field='{}' title='{}' → score={}",
                option.id(), option.field(), option.title(), score);
            if (score > bestScore) {
                bestScore = score;
                best = option;
            }
        }

        return best;
    }

    private String inferDomain(String combinedSkills) {
        int sw = countKeywordMatches(combinedSkills,
            "java", "spring", "jpa", "python", "react", "javascript", "typescript",
            "kotlin", "go", "rust", "node", "django", "flask", "vue", "angular",
            "backend", "frontend", "백엔드", "프론트엔드", "서버", "웹",
            "mysql", "postgresql", "redis", "kafka", "docker", "kubernetes",
            "aws", "gcp", "azure", "linux", "git", "ci", "cd",
            "tensorflow", "pytorch", "pandas", "ml", "ai", "데이터",
            "software", "소프트웨어", "개발", "프로그래밍", "알고리즘");
        int mechanical = countKeywordMatches(combinedSkills,
            "solidworks", "catia", "autocad", "기계", "설계", "유한요소",
            "열역학", "유체", "재료", "항공", "자동차", "금형", "cam", "cnc");
        int electrical = countKeywordMatches(combinedSkills,
            "전기", "전자", "회로", "pcb", "반도체", "임베디드", "embedded",
            "plc", "fpga", "vhdl", "verilog", "아날로그", "디지털");

        if (sw >= mechanical && sw >= electrical) return "SW";
        if (mechanical > sw && mechanical >= electrical) return "기계";
        if (electrical > sw && electrical > mechanical) return "전기전자";
        return "SW";
    }

    private int calcMatchScore(String field, String title, String department, String inferredDomain) {
        String combined = (field + " " + title + " " + department).toLowerCase();
        int score = 0;

        if ("SW".equals(inferredDomain)) {
            String[] keywords = {"sw", "소프트웨어", "컴퓨터", "it", "개발", "프로그래밍",
                "software", "데이터", "ai", "인공지능", "클라우드", "웹", "앱"};
            for (String kw : keywords) {
                if (combined.contains(kw)) score++;
            }
        } else if ("기계".equals(inferredDomain)) {
            String[] keywords = {"기계", "항공", "설계", "자동차", "mechanical", "메카", "생산"};
            for (String kw : keywords) {
                if (combined.contains(kw)) score++;
            }
        } else if ("전기전자".equals(inferredDomain)) {
            String[] keywords = {"전기", "전자", "회로", "반도체", "electrical", "electronic", "임베디드"};
            for (String kw : keywords) {
                if (combined.contains(kw)) score++;
            }
        }

        return score;
    }

    private int countKeywordMatches(String text, String... keywords) {
        int count = 0;
        for (String kw : keywords) {
            if (text.contains(kw)) count++;
        }
        return count;
    }

    /**
     * 원본 JD에서 회사 정보를 보존하고, employment 관련 섹션만 교체합니다.
     * 원본 JD 구조: [회사 소개]...[업종]...[홈페이지]...[직무 분야]...[포지션]...
     * → [직무 분야] 이전까지를 회사 정보로 보존하고, 이후를 refined로 교체합니다.
     */
    private String mergeJobDescriptions(String originalJd, String refinedEmploymentJd) {
        if (refinedEmploymentJd == null || refinedEmploymentJd.isBlank()) return originalJd;
        if (originalJd == null || originalJd.isBlank()) return refinedEmploymentJd;

        Set<String> employmentTags = Set.of("[직무 분야]", "[포지션]", "[부서]", "[경력 구분]", "[채용 설명]");

        StringBuilder companyInfo = new StringBuilder();
        for (String line : originalJd.split("\n")) {
            if (employmentTags.stream().anyMatch(line.trim()::startsWith)) break;
            companyInfo.append(line).append("\n");
        }

        String company = companyInfo.toString().stripTrailing();
        if (company.isBlank()) return refinedEmploymentJd;
        return company + "\n\n" + refinedEmploymentJd;
    }

    /**
     * fetchForEmployment 실패 시, EmploymentOption 기본 정보로 최소한의 JD를 구성합니다.
     */
    private String buildFallbackEmploymentJd(EmploymentOption option) {
        StringBuilder sb = new StringBuilder();
        if (option.field() != null && !option.field().isBlank())
            sb.append("[직무 분야] ").append(option.field()).append("\n");
        if (option.title() != null && !option.title().isBlank())
            sb.append("[포지션] ").append(option.title()).append("\n");
        if (option.department() != null && !option.department().isBlank())
            sb.append("[부서] ").append(option.department()).append("\n");
        return sb.toString();
    }

    private LocalDate parseDeadline(String deadline) {
        if (deadline == null || deadline.isBlank()) return null;
        try {
            String cleaned = deadline.contains("T") ? deadline.split("T")[0] : deadline;
            cleaned = cleaned.replaceAll("[^0-9\\-./]", "").trim();
            if (cleaned.contains(".")) cleaned = cleaned.replace(".", "-");
            if (cleaned.contains("/")) cleaned = cleaned.replace("/", "-");
            return LocalDate.parse(cleaned);
        } catch (Exception e) {
            log.debug("마감일 파싱 실패: {}", deadline);
            return null;
        }
    }

    private String serializeQuestions(List<EssayQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(questions);
        } catch (JsonProcessingException e) {
            log.warn("자소서 문항 직렬화 실패", e);
            return null;
        }
    }
}
