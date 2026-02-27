package com.career.assistant.application.github;

import java.util.List;

public record LearningRecommendation(
    List<LearningGap> gaps,
    List<DailyTask> todayTasks,
    List<CodingProblem> problems,
    List<CsQuiz> quizzes,
    BlogSuggestion blogTopic
) {
    public record LearningGap(
        String topic,
        int gapDays,
        String severity,
        String reason
    ) {}

    public record DailyTask(
        String category,
        String action,
        String reason
    ) {}

    public record CodingProblem(
        String platform,
        String number,
        String title,
        String type,
        String difficulty,
        String url
    ) {}

    public record CsQuiz(
        String topic,
        String question,
        List<String> options,
        int answer,
        String explanation
    ) {}

    public record BlogSuggestion(
        String title,
        String outline
    ) {}
}
