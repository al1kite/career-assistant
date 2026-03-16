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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverLetterFacade {

    private static final int MAX_ITERATIONS = 3;
    private static final int MIN_ITERATIONS = 2;
    private static final String QUALITY_GRADE = "A";

    private final JobPostingRepository jobPostingRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final ExperienceEmbeddingService experienceEmbeddingService;
    private final JsoupCrawler jsoupCrawler;
    private final CompanyClassifier companyClassifier;
    private final CompanyAnalyzer companyAnalyzer;
    private final CoverLetterPromptBuilder promptBuilder;
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

        // 기존 최신 버전 1회 조회 → 버전 번호 + 문항 텍스트 동시 추출
        var latestOpt = coverLetterRepository
            .findTopByJobPostingIdAndQuestionIndexOrderByVersionDesc(jobPostingId, qIdx);
        int nextVersion = latestOpt.map(cl -> cl.getVersion() + 1).orElse(1);
        String questionText = latestOpt.map(CoverLetter::getQuestionText).orElse(null);

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
        List<CoverLetter> results = new ArrayList<>();

        // essayQuestionsJson에서 문항별 charLimit 매핑
        Map<Integer, Integer> charLimitByQuestion = deserializeCharLimits(jp);

        log.info("[개선] 기존 자소서 추가 개선 시작 - 회사: {}, 문항 {}개", jp.getCompanyName(), latestByQuestion.size());

        for (CoverLetter latest : latestByQuestion.values()) {
            int qIdx = latest.getQuestionIndex() != null ? latest.getQuestionIndex() : 0;
            int charLimit = charLimitByQuestion.getOrDefault(qIdx, 1000);
            List<UserExperience> experiences = retrieveExperiencesOrFallback(jp, latest.getQuestionText());
            CoverLetter improved = generateWithReviewLoop(
                latest, jp, experiences, ai, latest.getQuestionText(), null, charLimit, userMessage);
            results.add(improved);

            log.info("[개선] 문항 {} 개선 완료 - v{} → v{}, 점수: {}",
                latest.getQuestionIndex(), latest.getVersion(), improved.getVersion(), improved.getReviewScore());
        }

        log.info("[개선] 추가 개선 완료 - 회사: {}, 문항 {}개", jp.getCompanyName(), results.size());
        return results;
    }

    @Transactional
    public List<CoverLetter> generateFromUrl(String url) {
        if (jobPostingRepository.existsByUrl(url)) {
            log.info("이미 처리된 공고: {}", url);
            JobPosting existing = jobPostingRepository.findByUrl(url).orElseThrow();

            // 자동수집으로 FETCHED 상태인 공고 → 크롤링/분석부터 다시 수행
            if (existing.needsCrawling()) {
                log.info("수집만 된 공고 — 크롤링/분석 시작: {}", url);
                return crawlAndGenerate(existing);
            }

            List<CoverLetter> existingLetters = coverLetterRepository.findByJobPostingId(existing.getId());
            if (!existingLetters.isEmpty()) {
                return existingLetters;
            }
            return generateCoverLetters(existing, List.of());
        }

        JobPosting jobPosting = JobPosting.from(url);
        jobPostingRepository.save(jobPosting);

        return crawlAndGenerate(jobPosting);
    }

    private List<CoverLetter> crawlAndGenerate(JobPosting jobPosting) {
        try {
            // 1단계: 크롤링
            CrawledJobInfo crawledInfo = jsoupCrawler.crawl(jobPosting.getUrl());
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

        jobPosting.markReviewing();

        if (essayQuestions == null || essayQuestions.isEmpty()) {
            List<UserExperience> experiences = retrieveExperiencesOrFallback(jobPosting, null);
            log.info("[RAG] 검색된 경험 {}건 (단일 자소서)", experiences.size());

            String prompt = promptBuilder.build(jobPosting, experiences, 1000);
            String content = enforceCharLimit(ai.generate(prompt), 1000);

            CoverLetter coverLetter = CoverLetter.of(jobPosting, ai.getModelName(), content);
            coverLetterRepository.save(coverLetter);

            CoverLetter finalLetter = generateWithReviewLoop(
                coverLetter, jobPosting, experiences, ai, null, null, 1000, null
            );

            jobPosting.markFinalized();
            log.info("[에이전트] 자소서 완료 (단일) - 회사: {}, 최종 v{}, 점수: {}",
                jobPosting.getCompanyName(), finalLetter.getVersion(), finalLetter.getReviewScore());
            return List.of(finalLetter);
        }

        List<CoverLetter> finalLetters = new ArrayList<>();
        for (EssayQuestion question : essayQuestions) {
            List<UserExperience> experiences = retrieveExperiencesOrFallback(jobPosting, question.questionText());
            log.info("[RAG] 문항 {} 검색된 경험 {}건", question.number(), experiences.size());

            String prompt = promptBuilder.buildForQuestion(jobPosting, experiences, question);
            int charLimit = question.charLimit() > 0 ? question.charLimit() : 1000;
            String content = enforceCharLimit(ai.generate(prompt), charLimit);

            CoverLetter coverLetter = CoverLetter.of(
                jobPosting, ai.getModelName(), content,
                question.number(), question.questionText()
            );
            coverLetterRepository.save(coverLetter);

            CoverLetter finalLetter = generateWithReviewLoop(
                coverLetter, jobPosting, experiences, ai,
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
                String improvedContent = enforceCharLimit(ai.generate(improvementPrompt), charLimit);

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
            case "answerRelevance" -> "첫 문장부터 질문 키워드에 직접 응답하세요. 질문이 묻는 것에 정면으로 답하세요.";
            case "jobFit" -> "채용공고 자격요건의 기술 키워드를 본인 경험과 직접 연결하세요. 구체적 프로젝트와 성과를 매칭하세요.";
            case "orgFit" -> "회사 분석의 핵심 가치/문화를 구체적으로 언급하세요. 이 회사만의 특성이 드러나야 합니다.";
            case "specificity" -> "'많은 개선'→'응답시간 2.3초→0.4초'로 교체하세요. 숫자, 프로젝트명, KPI를 반드시 포함하세요.";
            case "authenticity" -> "이 지원자만 쓸 수 있는 구체적 장면을 추가하세요. 날짜, 시간, 감정, 오감 디테일을 녹이세요.";
            case "aiDetectionRisk" -> "어미 반복을 깨고, 구어체 전환어('솔직히', '돌이켜보면')를 추가하고, 감정 표현을 넣으세요.";
            case "logicalStructure" -> "기승전결 순서를 점검하세요. 단락 간 논리 연결이 자연스러운지 확인하고, 비약이 있으면 연결 문장을 추가하세요.";
            case "keywordUsage" -> "채용공고의 핵심 키워드 3~5개를 추출하여 문맥에 맞게 자연스럽게 포함하세요.";
            case "experienceConsistency" -> "제공된 경험 목록에 없는 프로젝트나 경력을 삭제하세요. 실제 경험만 정확히 인용하세요.";
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
        try {
            String query = buildRetrievalQuery(jobPosting, questionText);
            return experienceEmbeddingService.retrieveRelevant(query);
        } catch (Exception e) {
            log.warn("[RAG] 벡터 검색 실패 — findAll 폴백: {}", e.getMessage());
            return userExperienceRepository.findAll();
        }
    }

    private String buildRetrievalQuery(JobPosting jobPosting, String questionText) {
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
        if (questionText != null) {
            sb.append(questionText);
        }
        return sb.toString().trim();
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
