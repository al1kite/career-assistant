package com.career.assistant.domain.experience;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserExperienceRepository extends JpaRepository<UserExperience, Long> {
    List<UserExperience> findByCategory(ExperienceCategory category);

    @Query("SELECT CASE WHEN COUNT(ue) > 0 THEN true ELSE false END FROM UserExperience ue")
    boolean existsAny();
}
