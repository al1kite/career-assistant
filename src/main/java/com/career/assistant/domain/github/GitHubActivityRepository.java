package com.career.assistant.domain.github;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GitHubActivityRepository extends JpaRepository<GitHubActivity, Long> {

    List<GitHubActivity> findByRepoName(String repoName);

    Optional<GitHubActivity> findByRepoNameAndTopic(String repoName, String topic);
}
