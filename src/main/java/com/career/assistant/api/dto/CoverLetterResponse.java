package com.career.assistant.api.dto;

import com.career.assistant.domain.coverletter.CoverLetter;

import java.time.LocalDateTime;

public record CoverLetterResponse(
    Long id,
    Long jobPostingId,
    String companyName,
    String aiModel,
    String content,
    int version,
    Integer questionIndex,
    String questionText,
    String feedback,
    Integer reviewScore,
    LocalDateTime createdAt
) {
    public static CoverLetterResponse from(CoverLetter cl) {
        return new CoverLetterResponse(
            cl.getId(),
            cl.getJobPosting().getId(),
            cl.getJobPosting().getCompanyName(),
            cl.getAiModel(),
            cl.getContent(),
            cl.getVersion(),
            cl.getQuestionIndex(),
            cl.getQuestionText(),
            cl.getFeedback(),
            cl.getReviewScore(),
            cl.getCreatedAt()
        );
    }
}
