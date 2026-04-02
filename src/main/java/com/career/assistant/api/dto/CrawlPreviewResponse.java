package com.career.assistant.api.dto;

import com.career.assistant.infrastructure.crawling.CrawledJobInfo;
import com.career.assistant.infrastructure.crawling.EmploymentOption;
import com.career.assistant.infrastructure.crawling.EssayQuestion;

import java.util.List;

public record CrawlPreviewResponse(
    String companyName,
    String jobDescription,
    String requirements,
    String deadline,
    boolean active,
    List<EssayQuestionDto> essayQuestions,
    List<EmploymentOptionDto> employmentOptions
) {
    public record EssayQuestionDto(int number, String questionText, int charLimit) {
        public static EssayQuestionDto from(EssayQuestion eq) {
            return new EssayQuestionDto(eq.number(), eq.questionText(), eq.charLimit());
        }
    }

    public record EmploymentOptionDto(int id, String field, String title, String department, int resumesCount) {
        public static EmploymentOptionDto from(EmploymentOption eo) {
            return new EmploymentOptionDto(eo.id(), eo.field(), eo.title(), eo.department(), eo.resumesCount());
        }
    }

    public static CrawlPreviewResponse from(CrawledJobInfo info) {
        return new CrawlPreviewResponse(
            info.companyName(),
            info.jobDescription(),
            info.requirements(),
            info.deadline(),
            info.active(),
            info.essayQuestions().stream()
                .map(EssayQuestionDto::from)
                .toList(),
            info.employmentOptions().stream()
                .map(EmploymentOptionDto::from)
                .toList()
        );
    }
}
