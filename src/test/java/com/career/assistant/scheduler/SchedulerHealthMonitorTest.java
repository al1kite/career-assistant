package com.career.assistant.scheduler;

import com.career.assistant.infrastructure.telegram.TelegramBotHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SchedulerHealthMonitorTest {

    @Mock
    private TelegramBotHandler telegramBotHandler;

    private SchedulerHealthMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new SchedulerHealthMonitor(telegramBotHandler);
    }

    @Test
    void 연속실패_임계값_미만이면_알림_안보냄() {
        RuntimeException ex = new RuntimeException("test error");

        monitor.recordFailure("테스트 태스크", ex);
        monitor.recordFailure("테스트 태스크", ex);

        verify(telegramBotHandler, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 연속실패_3회_도달시_알림_전송() {
        RuntimeException ex = new RuntimeException("test error");

        monitor.recordFailure("테스트 태스크", ex);
        monitor.recordFailure("테스트 태스크", ex);
        monitor.recordFailure("테스트 태스크", ex);

        verify(telegramBotHandler, times(1)).sendMessage(contains("연속 3회 실패"));
    }

    @Test
    void 알림은_임계값_도달시_1회만_전송() {
        RuntimeException ex = new RuntimeException("test error");

        for (int i = 0; i < 5; i++) {
            monitor.recordFailure("테스트 태스크", ex);
        }

        verify(telegramBotHandler, times(1)).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 성공시_카운터_리셋() {
        RuntimeException ex = new RuntimeException("test error");

        monitor.recordFailure("테스트 태스크", ex);
        monitor.recordFailure("테스트 태스크", ex);
        monitor.recordSuccess("테스트 태스크");
        monitor.recordFailure("테스트 태스크", ex);
        monitor.recordFailure("테스트 태스크", ex);

        verify(telegramBotHandler, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 리셋_후_재실패_3회시_알림_재전송() {
        RuntimeException ex = new RuntimeException("test error");

        // 첫 번째 3회 실패 → 알림
        for (int i = 0; i < 3; i++) {
            monitor.recordFailure("테스트 태스크", ex);
        }
        // 복구
        monitor.recordSuccess("테스트 태스크");
        // 두 번째 3회 실패 → 알림
        for (int i = 0; i < 3; i++) {
            monitor.recordFailure("테스트 태스크", ex);
        }

        verify(telegramBotHandler, times(2)).sendMessage(contains("연속 3회 실패"));
    }

    @Test
    void 태스크별_독립적_카운터() {
        RuntimeException ex = new RuntimeException("test error");

        monitor.recordFailure("태스크A", ex);
        monitor.recordFailure("태스크A", ex);
        monitor.recordFailure("태스크B", ex);
        monitor.recordFailure("태스크A", ex); // A만 3회 도달

        verify(telegramBotHandler, times(1)).sendMessage(contains("태스크A"));
        verify(telegramBotHandler, never()).sendMessage(contains("태스크B"));
    }

    @Test
    void 실패_없는_태스크에_성공_호출해도_에러없음() {
        monitor.recordSuccess("미등록 태스크");

        verify(telegramBotHandler, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }
}
