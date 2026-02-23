package com.career.assistant.domain.experience;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserExperienceRepository extends JpaRepository<UserExperience, Long> {
    List<UserExperience> findByCategory(ExperienceCategory category);
}
