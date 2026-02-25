package com.career.assistant.api;

import com.career.assistant.application.github.GitHubAnalyzer;
import com.career.assistant.domain.github.GitHubActivity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "GitHub Activity", description = "GitHub 커밋 분석 API")
@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GitHubActivityController {

    private final GitHubAnalyzer gitHubAnalyzer;

    @Operation(summary = "전체 GitHub 활동 조회")
    @GetMapping("/activities")
    public ResponseEntity<List<GitHubActivity>> getAll() {
        return ResponseEntity.ok(gitHubAnalyzer.getActivities());
    }

    @Operation(summary = "레포별 GitHub 활동 조회")
    @GetMapping("/activities/{repoName}")
    public ResponseEntity<List<GitHubActivity>> getByRepo(@PathVariable String repoName) {
        return ResponseEntity.ok(gitHubAnalyzer.getActivitiesByRepo(repoName));
    }

    @Operation(summary = "수동 동기화 트리거")
    @PostMapping("/sync")
    public ResponseEntity<String> sync() {
        gitHubAnalyzer.syncAll();
        return ResponseEntity.ok("GitHub sync completed");
    }
}
