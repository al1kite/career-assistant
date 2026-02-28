package com.career.assistant.application.github;

import com.career.assistant.infrastructure.telegram.TelegramBotHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingService {

    private final GitHubAnalyzer gitHubAnalyzer;
    private final LearningAdvisor learningAdvisor;
    private final ContentGenerator contentGenerator;
    private final BriefingMessageFormatter formatter;
    private final TelegramBotHandler telegramBotHandler;

    public void executeBriefing() {
        log.info("Morning briefing started");

        try {
            gitHubAnalyzer.syncAll();

            LearningRecommendation rec;
            try {
                rec = learningAdvisor.analyze();
            } catch (LearningAdvisor.NoActivityDataException e) {
                log.warn("No activity data for briefing: {}", e.getMessage());
                telegramBotHandler.sendMessage("GitHub 활동 데이터가 없어 브리핑을 생성할 수 없습니다.");
                return;
            }

            String briefing = formatter.formatBriefing(rec);
            telegramBotHandler.sendMessage(briefing);

            String quizMsg = formatter.formatQuizzes(rec.quizzes());
            if (quizMsg != null) {
                telegramBotHandler.sendMessage(quizMsg);
            }

            String problemMsg = formatter.formatProblems(rec.problems());
            if (problemMsg != null) {
                telegramBotHandler.sendMessage(problemMsg);
            }

            if (rec.blogTopic() != null) {
                String blogPost = contentGenerator.generateBlogPost(rec.blogTopic());
                if (blogPost != null) {
                    telegramBotHandler.sendMessage("[블로그] " + rec.blogTopic().title()
                        + "\n════════════════════\n\n" + blogPost);
                }
            }

            if (rec.gaps() != null && !rec.gaps().isEmpty()) {
                var urgentGap = findMostUrgentGap(rec);
                String csNote = contentGenerator.generateCsStudyNote(urgentGap);
                if (csNote != null) {
                    telegramBotHandler.sendMessage("[CS] " + urgentGap.topic()
                        + "\n════════════════════\n\n" + csNote);
                }
            }

            log.info("Morning briefing completed");

        } catch (Exception e) {
            log.error("Morning briefing failed", e);
            telegramBotHandler.sendMessage("아침 브리핑 생성 중 오류가 발생했습니다. 로그를 확인해주세요.");
        }
    }

    private LearningRecommendation.LearningGap findMostUrgentGap(LearningRecommendation rec) {
        return rec.gaps().stream()
            .max(Comparator.comparingInt(this::severityPriority)
                .thenComparingInt(LearningRecommendation.LearningGap::gapDays))
            .orElse(rec.gaps().get(0));
    }

    private int severityPriority(LearningRecommendation.LearningGap gap) {
        if (gap.severity() == null) return 0;
        return switch (gap.severity()) {
            case "긴급" -> 2;
            case "주의" -> 1;
            default -> 0;
        };
    }
}
