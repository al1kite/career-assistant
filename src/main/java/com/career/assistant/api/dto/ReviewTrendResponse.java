package com.career.assistant.api.dto;

import java.util.List;
import java.util.Map;

public record ReviewTrendResponse(
    String companyName,
    List<ScoreChange> trend
) {
    public record ScoreChange(
        int version,
        int totalScore,
        Map<String, Integer> dimensionScores,
        List<String> improvements
    ) {}
}
