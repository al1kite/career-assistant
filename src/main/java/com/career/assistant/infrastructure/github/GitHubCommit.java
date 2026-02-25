package com.career.assistant.infrastructure.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommit(String sha, Commit commit) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Commit(String message, Author author) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(String name, String date) {}

    public String getMessage() {
        return commit != null ? commit.message() : "";
    }

    public LocalDateTime getDate() {
        if (commit == null || commit.author() == null || commit.author().date() == null) {
            return null;
        }
        return OffsetDateTime.parse(commit.author().date()).toLocalDateTime();
    }
}
