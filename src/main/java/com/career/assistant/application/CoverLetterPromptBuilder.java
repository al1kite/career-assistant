package com.career.assistant.application;

import com.career.assistant.domain.experience.UserExperience;
import com.career.assistant.domain.jobposting.CompanyType;
import com.career.assistant.domain.jobposting.JobPosting;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CoverLetterPromptBuilder {

    public String build(JobPosting jobPosting, List<UserExperience> experiences) {
        String experienceSummary = experiences.stream()
            .map(e -> "[%s] %s (%s)\n%s".formatted(
                e.getCategory(), e.getTitle(), e.getPeriod(), e.getDescription()))
            .collect(Collectors.joining("\n\n"));

        String tone = resolveTone(jobPosting.getCompanyType());

        return """
            당신은 취업 자소서 전문가입니다. 아래 지원자의 경험과 채용공고를 바탕으로 자소서 초안을 작성해주세요.
            
            ## 작성 톤
            %s
            
            ## 지원자 경험
            %s
            
            ## 채용공고
            회사명: %s
            직무 설명: %s
            자격 요건: %s
            
            ## 작성 규칙
            - 지원 동기 (2~3문장)
            - 핵심 역량 및 경험 (경험 중 가장 관련 높은 것 2~3개 STAR 기법으로)
            - 입사 후 포부 (2~3문장)
            - 총 800~1000자 내외
            - 과장하지 말고 실제 경험 기반으로만 작성
            """.formatted(
                tone,
                experienceSummary,
                jobPosting.getCompanyName(),
                jobPosting.getJobDescription(),
                jobPosting.getRequirements()
            );
    }

    private String resolveTone(CompanyType companyType) {
        return switch (companyType) {
            case LARGE_CORP -> "공식적이고 안정감 있는 문체. 협업과 책임감 강조.";
            case FINANCE -> "정확하고 신뢰감 있는 문체. 꼼꼼함과 문제 해결력 강조.";
            case STARTUP -> "적극적이고 도전적인 문체. 주도적 경험과 성장 강조.";
            case MID_IT, UNKNOWN -> "전문적이고 실무 중심 문체. 기술 역량과 빠른 적응력 강조.";
        };
    }
}
