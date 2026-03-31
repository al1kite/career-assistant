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

    private static final String TASK_NAME = "아침 브리핑";

    private final BriefingService briefingService;
    private final SchedulerHealthMonitor healthMonitor;

    @Scheduled(cron = "0 0 6 * * MON-FRI", zone = "Asia/Seoul")
    public void sendMorningBriefing() {
        try {
            boolean success = briefingService.executeBriefing();
            if (success) {
                healthMonitor.recordSuccess(TASK_NAME);
            } else {
                healthMonitor.recordFailure(TASK_NAME,
                    new RuntimeException("브리핑 내부 실패 (상세 로그 확인)"));
            }
        } catch (Exception e) {
            log.error("아침 브리핑 스케줄러 실패", e);
            healthMonitor.recordFailure(TASK_NAME, e);
        }
    }
}
