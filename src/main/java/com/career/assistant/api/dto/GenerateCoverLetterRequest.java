package com.career.assistant.api.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateCoverLetterRequest(
    @NotBlank(message = "채용공고 URL은 필수입니다") String url
) {}
