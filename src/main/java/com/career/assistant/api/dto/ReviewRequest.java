package com.career.assistant.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(
    @NotBlank(message = "자소서 내용은 필수입니다") String content,
    Integer questionIndex
) {}
