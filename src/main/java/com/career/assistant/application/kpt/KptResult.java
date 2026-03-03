package com.career.assistant.application.kpt;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KptResult(
    List<String> keep,
    List<String> problem,
    @JsonProperty("try") List<String> tryActions,
    String comment,
    int completionRate
) {}
