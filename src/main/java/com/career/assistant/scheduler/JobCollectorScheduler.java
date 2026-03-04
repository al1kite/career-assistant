package com.career.assistant.scheduler;

import com.career.assistant.application.jobcollector.JobCollectorMessageFormatter;
import com.career.assistant.application.jobcollector.JobCollectorService;
import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.infrastructure.telegram.TelegramBotHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "job-collector.enabled", havingValue = "true")
public class JobCollectorScheduler {

    private final JobCollectorService jobCollectorService;
    private final JobCollectorMessageFormatter messageFormatter;
    private final TelegramBotHandler telegramBotHandler;

    @Scheduled(cron = "${job-collector.cron-collect:0 0 8 * * *}", zone = "Asia/Seoul")
    public void collectNewPostings() {
        log.info("채용공고 수집 스케줄러 시작");

        try {
            List<JobPosting> newPostings = jobCollectorService.collectNewPostings();
            String message = messageFormatter.formatNewPostings(newPostings);
            if (message != null) {
                telegramBotHandler.sendMessage(message);
            }
            log.info("채용공고 수집 완료 — 신규 {}건", newPostings.size());
        } catch (Exception e) {
            log.error("채용공고 수집 실패", e);
            telegramBotHandler.sendMessage("채용공고 수집에 실패했습니다. 로그를 확인해주세요.");
        }
    }

    @Scheduled(cron = "${job-collector.cron-deadline:0 0 9 * * *}", zone = "Asia/Seoul")
    public void sendDeadlineAlerts() {
        log.info("마감 임박 알림 스케줄러 시작");

        try {
            List<JobPosting> upcoming = jobCollectorService.findUpcomingDeadlines();
            String message = messageFormatter.formatDeadlineAlerts(upcoming);
            if (message != null) {
                telegramBotHandler.sendMessage(message);
            }
            log.info("마감 임박 알림 완료 — {}건", upcoming.size());
        } catch (Exception e) {
            log.error("마감 임박 알림 실패", e);
        }
    }
}
