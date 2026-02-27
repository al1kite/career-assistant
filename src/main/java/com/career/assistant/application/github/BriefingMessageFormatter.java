package com.career.assistant.application.github;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class BriefingMessageFormatter {

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN);

    public String formatBriefing(LearningRecommendation rec) {
        StringBuilder sb = new StringBuilder();
        sb.append("아침 브리핑 — ").append(LocalDate.now().format(DATE_FMT)).append("\n");
        sb.append("════════════════════\n\n");

        if (rec.gaps() != null && !rec.gaps().isEmpty()) {
            sb.append("학습 공백 알림\n");
            for (var gap : rec.gaps()) {
                String label = resolveSeverityLabel(gap.severity());
                sb.append("  ").append(label).append(" ").append(gap.reason()).append("\n");
            }
            sb.append("\n");
        }

        if (rec.todayTasks() != null && !rec.todayTasks().isEmpty()) {
            sb.append("오늘 할 일\n");
            int i = 1;
            for (var task : rec.todayTasks()) {
                sb.append("  ").append(i++).append(". ")
                    .append("[").append(task.category()).append("] ")
                    .append(task.action()).append("\n");
            }
        }

        return sb.toString();
    }

    public String formatQuizzes(List<LearningRecommendation.CsQuiz> quizzes) {
        if (quizzes == null || quizzes.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("CS 퀴즈 — ").append(quizzes.get(0).topic()).append("\n");
        sb.append("────────────\n\n");

        int qNum = 1;
        for (var quiz : quizzes) {
            sb.append("Q").append(qNum++).append(". ").append(quiz.question()).append("\n");
            if (quiz.options() != null) {
                char opt = 'A';
                for (String option : quiz.options()) {
                    sb.append("  ").append(opt++).append(") ").append(option).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("정답은 아래에서 확인하세요!\n\n");

        qNum = 1;
        for (var quiz : quizzes) {
            char answerChar = normalizeAnswerIndex(quiz.answer(), quiz.options());
            sb.append("Q").append(qNum++).append(" 정답: ").append(answerChar);
            if (quiz.explanation() != null) {
                sb.append("\n  -> ").append(quiz.explanation());
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public String formatProblems(List<LearningRecommendation.CodingProblem> problems) {
        if (problems == null || problems.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("코테 문제 추천\n");
        sb.append("────────────\n\n");

        int i = 1;
        for (var p : problems) {
            sb.append(i++).append(". [").append(p.type()).append("] ")
                .append(p.platform()).append(" ").append(p.title())
                .append(" (").append(p.difficulty()).append(")\n");
            if (p.url() != null && !p.url().isBlank()) {
                sb.append("   ").append(p.url()).append("\n");
            }
        }

        return sb.toString();
    }

    private String resolveSeverityLabel(String severity) {
        if (severity == null) return "[양호]";
        return switch (severity) {
            case "긴급" -> "[긴급]";
            case "주의" -> "[주의]";
            default -> "[양호]";
        };
    }

    private char normalizeAnswerIndex(Integer answer, List<String> options) {
        if (answer == null) return '?';

        int optionCount = (options != null) ? options.size() : 4;

        // 0-based (0~3) 허용
        if (answer >= 0 && answer < optionCount) {
            return (char) ('A' + answer);
        }
        // 1-based (1~4) 허용 → 0-based로 변환
        if (answer >= 1 && answer <= optionCount) {
            return (char) ('A' + answer - 1);
        }

        return '?';
    }
}
