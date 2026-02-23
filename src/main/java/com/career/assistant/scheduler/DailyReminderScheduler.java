package com.career.assistant.scheduler;

import com.career.assistant.infrastructure.telegram.TelegramBotHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyReminderScheduler {

    private final TelegramBotHandler telegramBotHandler;

    // 매일 오전 9시
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void morningBriefing() {
        telegramBotHandler.sendMessage(
            "🌅 좋은 아침입니다!\n\n" +
            "오늘 자소서 1~2개 목표입니다 💪\n" +
            "지원할 공고 링크를 보내주세요!"
        );
    }

    // 매일 밤 10시
    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
    public void eveningCheck() {
        telegramBotHandler.sendMessage(
            "🌙 오늘 하루 어땠나요?\n\n" +
            "오늘 자소서 작성하셨나요? ✍️\n" +
            "내일도 화이팅입니다!"
        );
    }
}
