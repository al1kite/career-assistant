package com.career.assistant.scheduler;

import com.career.assistant.application.github.GitHubAnalyzer;
import com.career.assistant.application.kpt.KptAnalyzer;
import com.career.assistant.application.kpt.KptMessageFormatter;
import com.career.assistant.domain.kpt.KptRecord;
import com.career.assistant.infrastructure.telegram.TelegramBotHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KptScheduler {

    private final GitHubAnalyzer gitHubAnalyzer;
    private final KptAnalyzer kptAnalyzer;
    private final KptMessageFormatter kptMessageFormatter;
    private final TelegramBotHandler telegramBotHandler;

    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
    public void executeKptAnalysis() {
        log.info("KPT evening analysis started");

        try {
            gitHubAnalyzer.syncAll();
            KptRecord record = kptAnalyzer.analyze();
            String formatted = kptMessageFormatter.formatResult(record);
            telegramBotHandler.sendMessage(formatted);
            log.info("KPT evening analysis completed");
        } catch (Exception e) {
            log.error("KPT analysis failed", e);
            telegramBotHandler.sendMessage("KPT 분석에 실패했습니다. 로그를 확인해주세요.");
        }
    }
}
