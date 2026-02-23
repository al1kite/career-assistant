package com.career.assistant.api.dto;

import com.career.assistant.domain.coverletter.CoverLetter;

import java.time.LocalDateTime;
import java.util.List;

public record CoverLetterAgentResponse(
    Integer questionIndex,
    String questionText,
    List<IterationDetail> iterations,
    FinalResult finalResult
) {

    public record IterationDetail(
        int version,
        String content,
        Integer reviewScore,
        String feedback,
        LocalDateTime createdAt
    ) {
        public static IterationDetail from(CoverLetter cl) {
            return new IterationDetail(
                cl.getVersion(),
                cl.getContent(),
                cl.getReviewScore(),
                cl.getFeedback(),
                cl.getCreatedAt()
            );
        }
    }

    public record FinalResult(
        Long id,
        String content,
        int version,
        Integer reviewScore,
        String grade,
        String aiModel
    ) {
        public static FinalResult from(CoverLetter cl) {
            int score = cl.getReviewScore() != null ? cl.getReviewScore() : 0;
            String grade = resolveGrade(score);
            return new FinalResult(
                cl.getId(),
                cl.getContent(),
                cl.getVersion(),
                cl.getReviewScore(),
                grade,
                cl.getAiModel()
            );
        }

        private static String resolveGrade(int score) {
            if (score >= 90) return "S";
            if (score >= 80) return "A";
            if (score >= 70) return "B";
            if (score >= 60) return "C";
            return "D";
        }
    }
}
