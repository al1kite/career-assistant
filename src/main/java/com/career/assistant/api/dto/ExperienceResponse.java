package com.career.assistant.api.dto;

import com.career.assistant.domain.experience.ExperienceCategory;
import com.career.assistant.domain.experience.UserExperience;

public record ExperienceResponse(
    Long id,
    ExperienceCategory category,
    String title,
    String description,
    String skills,
    String period
) {
    public static ExperienceResponse from(UserExperience exp) {
        return new ExperienceResponse(
            exp.getId(),
            exp.getCategory(),
            exp.getTitle(),
            exp.getDescription(),
            exp.getSkills(),
            exp.getPeriod()
        );
    }
}
