package com.career.assistant.domain.coverletter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {
    List<CoverLetter> findByJobPostingId(Long jobPostingId);
}
