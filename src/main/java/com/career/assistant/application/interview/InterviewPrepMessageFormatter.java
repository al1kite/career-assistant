package com.career.assistant.application.interview;

import com.career.assistant.application.interview.InterviewPrepResult.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterviewPrepMessageFormatter {

    public String format(String companyName, InterviewPrepResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(companyName).append(" 면접 준비 가이드\n");
        sb.append("════════════════════\n\n");

        appendQuestions(sb, "인성 면접", result.behavioralQuestions());
        appendQuestions(sb, "직무/기술 면접", result.technicalQuestions());
        appendQuestions(sb, "경험 기반 질문", result.experienceQuestions());

        if (result.codingTestPrep() != null && result.codingTestPrep().hasCodingTest()) {
            appendCodingTestPrep(sb, result.codingTestPrep());
        }

        return sb.toString();
    }

    private void appendQuestions(StringBuilder sb, String sectionTitle,
                                  List<InterviewQuestion> questions) {
        if (questions == null || questions.isEmpty()) return;

        sb.append("[").append(sectionTitle).append("]\n");
        for (int i = 0; i < questions.size(); i++) {
            InterviewQuestion q = questions.get(i);
            sb.append("Q").append(i + 1).append(". ").append(q.question()).append("\n");
            sb.append("  출제 의도: ").append(q.intent()).append("\n");
            sb.append("  답변 가이드: ").append(q.answerGuide()).append("\n");
            sb.append("\n");
        }
    }

    private void appendCodingTestPrep(StringBuilder sb, CodingTestPrep prep) {
        sb.append("[코딩테스트 대비]\n");

        if (prep.testFormat() != null && !prep.testFormat().isBlank()) {
            sb.append("예상 형식: ").append(prep.testFormat()).append("\n");
        }

        if (prep.keyTopics() != null && !prep.keyTopics().isEmpty()) {
            sb.append("핵심 준비 주제: ").append(String.join(", ", prep.keyTopics())).append("\n");
        }

        if (prep.practiceProblems() != null && !prep.practiceProblems().isEmpty()) {
            sb.append("추천 연습 문제:\n");
            for (int i = 0; i < prep.practiceProblems().size(); i++) {
                PracticeProblem p = prep.practiceProblems().get(i);
                sb.append("  ").append(i + 1).append(". ");
                sb.append("[").append(p.difficulty()).append("] ");
                sb.append(p.title()).append(" — ").append(p.topic()).append("\n");
                if (p.description() != null && !p.description().isBlank()) {
                    sb.append("     ").append(p.description()).append("\n");
                }
            }
        }
        sb.append("\n");
    }
}
