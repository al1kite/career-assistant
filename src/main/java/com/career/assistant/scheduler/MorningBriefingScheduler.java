package com.career.assistant.scheduler;

import com.career.assistant.application.github.*;
import com.career.assistant.infrastructure.telegram.TelegramBotHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MorningBriefingScheduler {

    private static final int TELEGRAM_MAX_LENGTH = 4096;

    private final GitHubAnalyzer gitHubAnalyzer;
    private final LearningAdvisor learningAdvisor;
    private final ContentGenerator contentGenerator;
    private final BriefingMessageFormatter formatter;
    private final TelegramBotHandler telegramBotHandler;

    @Scheduled(cron = "0 0 6 * * MON-FRI", zone = "Asia/Seoul")
    public void sendMorningBriefing() {
        log.info("Morning briefing started");

        try {
            // 1. GitHub 동기화
            gitHubAnalyzer.syncAll();

            // 2. AI 분석
            LearningRecommendation rec;
            try {
                rec = learningAdvisor.analyze();
            } catch (LearningAdvisor.NoActivityDataException e) {
                log.warn("No activity data for briefing: {}", e.getMessage());
                telegramBotHandler.sendMessage("GitHub 활동 데이터가 없어 브리핑을 생성할 수 없습니다.");
                return;
            }

            // 3. 오늘 브리핑 전송
            String briefing = formatter.formatBriefing(rec);
            telegramBotHandler.sendMessage(briefing);

            // 4. CS 퀴즈 전송
            String quizMsg = formatter.formatQuizzes(rec.quizzes());
            if (quizMsg != null) {
                telegramBotHandler.sendMessage(quizMsg);
            }

            // 5. 코테 문제 추천 전송
            String problemMsg = formatter.formatProblems(rec.problems());
            if (problemMsg != null) {
                telegramBotHandler.sendMessage(problemMsg);
            }

            // 6. 블로그 글 생성 + 전송
            if (rec.blogTopic() != null) {
                String blogPost = contentGenerator.generateBlogPost(rec.blogTopic());
                if (blogPost != null) {
                    sendLongMessage("[블로그] " + rec.blogTopic().title(), blogPost);
                }
            }

            // 7. CS 스터디 노트 생성 + 전송 (가장 긴급한 공백 기준)
            if (rec.gaps() != null && !rec.gaps().isEmpty()) {
                var urgentGap = rec.gaps().stream()
                    .filter(g -> "긴급".equals(g.severity()) || "주의".equals(g.severity()))
                    .findFirst()
                    .orElse(rec.gaps().get(0));

                String csNote = contentGenerator.generateCsStudyNote(urgentGap);
                if (csNote != null) {
                    sendLongMessage("[CS] " + urgentGap.topic(), csNote);
                }
            }

            log.info("Morning briefing completed");

        } catch (Exception e) {
            log.error("Morning briefing failed: {}", e.getMessage(), e);
            telegramBotHandler.sendMessage("아침 브리핑 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private void sendLongMessage(String title, String content) {
        String fullMessage = title + "\n════════════════════\n\n" + content;

        if (fullMessage.length() <= TELEGRAM_MAX_LENGTH) {
            telegramBotHandler.sendMessage(fullMessage);
            return;
        }

        // 4096자 제한으로 분할 전송
        telegramBotHandler.sendMessage(title + "\n════════════════════\n\n(긴 글이라 나눠서 보내드립니다)");

        int offset = 0;
        int part = 1;
        while (offset < content.length()) {
            int end = Math.min(offset + TELEGRAM_MAX_LENGTH - 20, content.length());

            // 줄바꿈 기준으로 자르기
            if (end < content.length()) {
                int lastNewline = content.lastIndexOf('\n', end);
                if (lastNewline > offset) {
                    end = lastNewline + 1;
                }
            }

            String chunk = content.substring(offset, end);
            telegramBotHandler.sendMessage("(%d) ".formatted(part++) + chunk);
            offset = end;
        }
    }
}
