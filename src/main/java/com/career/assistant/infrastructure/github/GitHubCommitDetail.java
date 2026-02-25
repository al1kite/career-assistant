package com.career.assistant.infrastructure.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitDetail(String sha, List<File> files) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record File(String filename, String status) {}
}
