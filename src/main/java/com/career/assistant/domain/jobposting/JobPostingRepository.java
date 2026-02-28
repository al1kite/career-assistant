package com.career.assistant.domain.jobposting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    Optional<JobPosting> findByUrl(String url);
    boolean existsByUrl(String url);
    List<JobPosting> findByCompanyNameContaining(String name);
}
