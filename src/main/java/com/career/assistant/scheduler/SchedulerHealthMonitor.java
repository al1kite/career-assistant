package com.career.assistant.scheduler;

import com.career.assistant.infrastructure.telegram.TelegramBotHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerHealthMonitor {

    private static final int ALERT_THRESHOLD = 3;

    private final TelegramBotHandler telegramBotHandler;
    private final ConcurrentHashMap<String, AtomicInteger> failureCounts = new ConcurrentHashMap<>();

    public void recordSuccess(String taskName) {
        AtomicInteger counter = failureCounts.get(taskName);
        if (counter != null && counter.get() > 0) {
            log.info("[헬스] {} 복구됨 (연속 실패 {}회 후 성공)", taskName, counter.get());
            counter.set(0);
        }
    }

    public void recordFailure(String taskName, Exception e) {
        int count = failureCounts
            .computeIfAbsent(taskName, k -> new AtomicInteger(0))
            .incrementAndGet();

        log.warn("[헬스] {} 실패 (연속 {}회)", taskName, count);

        if (count == ALERT_THRESHOLD) {
            String message = "[스케줄러 경고] %s가 연속 %d회 실패했습니다.\n마지막 오류: %s"
                .formatted(taskName, count, e.getMessage());
            telegramBotHandler.sendMessage(message);
        }
    }
}
