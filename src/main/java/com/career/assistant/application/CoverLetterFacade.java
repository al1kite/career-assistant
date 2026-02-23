package com.career.assistant.application;

import com.career.assistant.domain.coverletter.CoverLetter;
import com.career.assistant.domain.coverletter.CoverLetterRepository;
import com.career.assistant.domain.experience.UserExperience;
import com.career.assistant.domain.experience.UserExperienceRepository;
import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.domain.jobposting.JobPostingRepository;
import com.career.assistant.infrastructure.ai.AiPort;
import com.career.assistant.infrastructure.ai.AiRouter;
import com.career.assistant.infrastructure.crawling.CrawledJobInfo;
import com.career.assistant.infrastructure.crawling.JsoupCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoverLetterFacade {

    private final JobPostingRepository jobPostingRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final JsoupCrawler jsoupCrawler;
    private final CompanyClassifier companyClassifier;
    private final CoverLetterPromptBuilder promptBuilder;
    private final AiRouter aiRouter;

    @Transactional
    public CoverLetter generateFromUrl(String url) {
        if (jobPostingRepository.existsByUrl(url)) {
            log.info("이미 처리된 공고: {}", url);
            JobPosting existing = jobPostingRepository.findByUrl(url).orElseThrow();
            return coverLetterRepository.findByJobPostingId(existing.getId())
                .stream().findFirst()
                .orElseGet(() -> generateCoverLetter(existing));
        }

        JobPosting jobPosting = JobPosting.from(url);
        jobPostingRepository.save(jobPosting);

        try {
            // 1단계: 크롤링
            CrawledJobInfo crawledInfo = jsoupCrawler.crawl(url);
            jobPosting.updateCrawledInfo(
                crawledInfo.companyName(),
                crawledInfo.jobDescription(),
                crawledInfo.requirements()
            );

            // 2단계: 회사 유형 분류
            var companyType = companyClassifier.classify(
                crawledInfo.companyName(), crawledInfo.jobDescription());
            jobPosting.classify(companyType);

            // 3단계: 자소서 생성
            return generateCoverLetter(jobPosting);

        } catch (Exception e) {
            jobPosting.markFailed();
            log.error("자소서 생성 실패: {}", url, e);
            throw e;
        }
    }

    private CoverLetter generateCoverLetter(JobPosting jobPosting) {
        List<UserExperience> experiences = userExperienceRepository.findAll();
        AiPort ai = aiRouter.route(jobPosting.getCompanyType());
        String prompt = promptBuilder.build(jobPosting, experiences);
        String content = ai.generate(prompt);

        CoverLetter coverLetter = CoverLetter.of(jobPosting, ai.getModelName(), content);
        coverLetterRepository.save(coverLetter);
        jobPosting.markDrafted();

        log.info("자소서 생성 완료 - 회사: {}, 모델: {}",
            jobPosting.getCompanyName(), ai.getModelName());
        return coverLetter;
    }
}
