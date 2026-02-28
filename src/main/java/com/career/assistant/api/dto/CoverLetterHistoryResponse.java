package com.career.assistant.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CoverLetterHistoryResponse(
    String companyName,
    String questionText,
    List<VersionSnapshot> versions
) {
    public record VersionSnapshot(
        int version,
        String content,
        Integer reviewScore,
        String grade,
        String reviewSummary,
        LocalDateTime createdAt
    ) {}
}
