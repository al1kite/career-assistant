package com.career.assistant.scheduler;

import com.career.assistant.application.github.BriefingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MorningBriefingScheduler {

    private final BriefingService briefingService;

    @Scheduled(cron = "0 0 6 * * MON-FRI", zone = "Asia/Seoul")
    public void sendMorningBriefing() {
        briefingService.executeBriefing();
    }
}
