package com.career.assistant.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GitHubRepoRegistry {

    public record RepoEntry(String name, String label) {}

    private final String username;
    private final List<RepoEntry> trackedRepos;

    public GitHubRepoRegistry(
        @Value("${github.username}") String username,
        @Value("${github.repos.coding-test}") String codingTest,
        @Value("${github.repos.blog}") String blog,
        @Value("${github.repos.cs-study}") String csStudy,
        @Value("${github.repos.career-assistant:career-assistant}") String careerAssistant
    ) {
        this.username = username;
        this.trackedRepos = List.of(
            new RepoEntry(codingTest, "코딩테스트"),
            new RepoEntry(blog, "블로그"),
            new RepoEntry(csStudy, "CS 스터디"),
            new RepoEntry(careerAssistant, "자소서 프로젝트")
        );
    }

    public String getUsername() {
        return username;
    }

    public List<RepoEntry> getTrackedRepos() {
        return trackedRepos;
    }
}
