package com.career.assistant.application.interview;

import java.util.List;

public record InterviewPrepResult(
    List<InterviewQuestion> behavioralQuestions,
    List<InterviewQuestion> technicalQuestions,
    List<InterviewQuestion> experienceQuestions,
    CodingTestPrep codingTestPrep
) {
    public record InterviewQuestion(
        String question, String intent, String answerGuide
    ) {}

    public record CodingTestPrep(
        boolean hasCodingTest, String testFormat,
        List<String> keyTopics, List<PracticeProblem> practiceProblems
    ) {}

    public record PracticeProblem(
        String title, String difficulty, String topic, String description
    ) {}

    public static InterviewPrepResult fallback() {
        return new InterviewPrepResult(List.of(), List.of(), List.of(),
            new CodingTestPrep(false, null, List.of(), List.of()));
    }
}
