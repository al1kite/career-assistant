package com.career.assistant.domain.coverletter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {
    List<CoverLetter> findByJobPostingId(Long jobPostingId);

    List<CoverLetter> findByJobPostingIdIn(List<Long> jobPostingIds);

    List<CoverLetter> findByJobPostingIdAndQuestionIndexOrderByVersionAsc(Long jobPostingId, Integer questionIndex);

    Optional<CoverLetter> findTopByJobPostingIdAndQuestionIndexOrderByVersionDesc(Long jobPostingId, Integer questionIndex);
}
