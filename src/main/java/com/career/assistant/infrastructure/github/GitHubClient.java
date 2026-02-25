package com.career.assistant.infrastructure.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class GitHubClient {

    private final WebClient webClient;

    public GitHubClient(@Qualifier("githubWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public List<GitHubCommit> getCommits(String owner, String repo, LocalDateTime since) {
        String sinceStr = since.atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        try {
            List<GitHubCommit> commits = webClient.get()
                .uri("/repos/{owner}/{repo}/commits?since={since}&per_page=100", owner, repo, sinceStr)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    if (response.statusCode().value() == 404) {
                        log.warn("Repository not found: {}/{}", owner, repo);
                        return Mono.empty();
                    }
                    return response.createError();
                })
                .bodyToMono(new ParameterizedTypeReference<List<GitHubCommit>>() {})
                .onErrorReturn(Collections.emptyList())
                .block();

            if (commits != null) {
                log.info("Fetched {} commits from {}/{}", commits.size(), owner, repo);
                return commits;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch commits from {}/{}: {}", owner, repo, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<String> getCommitFiles(String owner, String repo, String sha) {
        try {
            GitHubCommitDetail detail = webClient.get()
                .uri("/repos/{owner}/{repo}/commits/{sha}", owner, repo, sha)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    if (response.statusCode().value() == 404) {
                        return Mono.empty();
                    }
                    return response.createError();
                })
                .bodyToMono(GitHubCommitDetail.class)
                .onErrorReturn(new GitHubCommitDetail(sha, Collections.emptyList()))
                .block();

            if (detail != null && detail.files() != null) {
                return detail.files().stream()
                    .map(GitHubCommitDetail.File::filename)
                    .toList();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch commit files for {}/{}/{}: {}", owner, repo, sha, e.getMessage());
            return Collections.emptyList();
        }
    }
}
