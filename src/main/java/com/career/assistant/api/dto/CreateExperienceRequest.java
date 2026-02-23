package com.career.assistant.api.dto;

import com.career.assistant.domain.experience.ExperienceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateExperienceRequest(
    @NotNull(message = "카테고리는 필수입니다") ExperienceCategory category,
    @NotBlank(message = "제목은 필수입니다") String title,
    @NotBlank(message = "설명은 필수입니다") String description,
    String skills,
    String period
) {}
