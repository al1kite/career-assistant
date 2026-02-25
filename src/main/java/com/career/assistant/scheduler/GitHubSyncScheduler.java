package com.career.assistant.scheduler;

import com.career.assistant.application.github.GitHubAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubSyncScheduler {

    private final GitHubAnalyzer gitHubAnalyzer;

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Seoul")
    public void sync() {
        log.info("Scheduled GitHub sync started");
        gitHubAnalyzer.syncAll();
        log.info("Scheduled GitHub sync finished");
    }
}
