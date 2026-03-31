package com.career.assistant.domain.coverletter;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {

    @EntityGraph(attributePaths = {"jobPosting"})
    List<CoverLetter> findByJobPostingId(Long jobPostingId);

    @EntityGraph(attributePaths = {"jobPosting"})
    List<CoverLetter> findByJobPostingIdIn(List<Long> jobPostingIds);

    @EntityGraph(attributePaths = {"jobPosting"})
    List<CoverLetter> findByJobPostingIdAndQuestionIndexOrderByVersionAsc(Long jobPostingId, Integer questionIndex);

    Optional<CoverLetter> findTopByJobPostingIdAndQuestionIndexOrderByVersionDesc(Long jobPostingId, Integer questionIndex);
}
