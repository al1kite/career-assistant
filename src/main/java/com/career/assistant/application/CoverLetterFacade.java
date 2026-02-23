package com.career.assistant.application;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverLetterFacade {

    private static final int MAX_ITERATIONS = 3;
    private static final int QUALITY_THRESHOLD = 85;

    private final JobPostingRepository jobPostingRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final JsoupCrawler jsoupCrawler;
    private final CompanyClassifier companyClassifier;
    private final CompanyAnalyzer companyAnalyzer;
    private final CoverLetterPromptBuilder promptBuilder;
    private final AiRouter aiRouter;
    private final ObjectMapper objectMapper;
    private final ReviewAgent reviewAgent;

    @Transactional
    public List<CoverLetter> generateFromUrl(String url) {
        if (jobPostingRepository.existsByUrl(url)) {
            log.info("이미 처리된 공고: {}", url);
            JobPosting existing = jobPostingRepository.findByUrl(url).orElseThrow();
            List<CoverLetter> existingLetters = coverLetterRepository.findByJobPostingId(existing.getId());
            if (!existingLetters.isEmpty()) {
                return existingLetters;
            }
            return generateCoverLetters(existing, List.of());
        }

        JobPosting jobPosting = JobPosting.from(url);
        jobPostingRepository.save(jobPosting);

        try {
            // 1단계: 크롤링
            CrawledJobInfo crawledInfo = jsoupCrawler.crawl(url);
            List<EssayQuestion> essayQuestions = crawledInfo.essayQuestions();

            String essayQuestionsJson = serializeQuestions(essayQuestions);

            jobPosting.updateCrawledInfo(
                crawledInfo.companyName(),
                crawledInfo.jobDescription(),
                crawledInfo.requirements(),
                essayQuestionsJson
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
            log.error("자소서 생성 실패: {}", url, e);
            throw e;
        }
    }

    private List<CoverLetter> generateCoverLetters(JobPosting jobPosting, List<EssayQuestion> essayQuestions) {
        List<UserExperience> experiences = userExperienceRepository.findAll();
        AiPort ai = aiRouter.route(jobPosting.getCompanyType());

        jobPosting.markReviewing();

        if (essayQuestions == null || essayQuestions.isEmpty()) {
            String prompt = promptBuilder.build(jobPosting, experiences);
            String content = ai.generate(prompt);

            CoverLetter coverLetter = CoverLetter.of(jobPosting, ai.getModelName(), content);
            coverLetterRepository.save(coverLetter);

            CoverLetter finalLetter = generateWithReviewLoop(
                coverLetter, jobPosting, experiences, ai, null, null
            );

            jobPosting.markFinalized();
            log.info("[에이전트] 자소서 완료 (단일) - 회사: {}, 최종 v{}, 점수: {}",
                jobPosting.getCompanyName(), finalLetter.getVersion(), finalLetter.getReviewScore());
            return List.of(finalLetter);
        }

        List<CoverLetter> finalLetters = new ArrayList<>();
        for (EssayQuestion question : essayQuestions) {
            String prompt = promptBuilder.buildForQuestion(jobPosting, experiences, question);
            String content = ai.generate(prompt);

            CoverLetter coverLetter = CoverLetter.of(
                jobPosting, ai.getModelName(), content,
                question.number(), question.questionText()
            );
            coverLetterRepository.save(coverLetter);

            CoverLetter finalLetter = generateWithReviewLoop(
                coverLetter, jobPosting, experiences, ai,
                question.questionText(), question
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
                                                String questionText, EssayQuestion essayQuestion) {
        String currentDraft = currentLetter.getContent();
        CoverLetter latest = currentLetter;

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            log.info("[에이전트] v{} → {}차 검토 시작 (문항: {})",
                latest.getVersion(), iteration,
                questionText != null ? questionText.substring(0, Math.min(20, questionText.length())) : "단일");

            // 검토
            ReviewResult review;
            try {
                review = reviewAgent.review(currentDraft, jobPosting, questionText, iteration);
            } catch (Exception e) {
                log.error("[에이전트] 검토 중 API 오류, 현재 버전을 최종으로 저장: {}", e.getMessage());
                break;
            }

            // 검토 결과 저장
            latest.addReview(review.rawJson(), review.totalScore());
            coverLetterRepository.save(latest);

            log.info("[에이전트] {}차 검토 결과 - 점수: {}점({}등급), violations: {}개, improvements: {}개",
                iteration, review.totalScore(), review.grade(),
                review.violations().size(), review.improvements().size());

            // 품질 임계값 통과
            if (review.totalScore() >= QUALITY_THRESHOLD) {
                log.info("[에이전트] 품질 기준 통과! ({}점 >= {}점)", review.totalScore(), QUALITY_THRESHOLD);
                break;
            }

            // 마지막 반복이면 더 이상 개선하지 않음
            if (iteration == MAX_ITERATIONS) {
                log.info("[에이전트] 최대 반복 횟수 도달 ({}회), 현재 버전으로 확정", MAX_ITERATIONS);
                break;
            }

            // 개선 프롬프트 생성 및 AI 호출
            log.info("[에이전트] 개선 프롬프트 생성 → v{} 작성 중...", latest.getVersion() + 1);
            try {
                String improvementPrompt = promptBuilder.buildImprovementPrompt(
                    jobPosting, experiences, questionText, currentDraft, review.rawJson(), iteration
                );
                String improvedContent = ai.generate(improvementPrompt);

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
                log.error("[에이전트] 개선 중 API 오류, 현재 버전을 최종으로 저장: {}", e.getMessage());
                break;
            }
        }

        return latest;
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
