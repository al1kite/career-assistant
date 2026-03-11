package com.career.assistant.api.dto;

import java.util.List;
import java.util.Map;

public record ReviewResponse(
    int totalScore,
    String grade,
    String overallComment,
    Map<String, Integer> scores,
    List<String> violations,
    List<String> improvements,
    Long coverLetterId,
    int version
) {}
