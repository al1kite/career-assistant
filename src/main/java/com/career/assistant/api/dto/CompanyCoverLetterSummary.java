package com.career.assistant.api.dto;

import java.util.List;

public record CompanyCoverLetterSummary(
    Long jobPostingId,
    String companyName,
    int questionCount,
    List<QuestionSummary> questions
) {
    public record QuestionSummary(
        int questionIndex,
        String questionText,
        int finalVersion,
        int reviewScore,
        String grade
    ) {}
}
