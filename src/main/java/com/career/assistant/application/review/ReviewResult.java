package com.career.assistant.application.review;

public record ReviewResult(
    Scores scores,
    int totalScore,
    String grade,
    java.util.List<String> violations,
    java.util.List<String> improvements,
    String overallComment,
    String rawJson
) {

    public record Scores(
        int answerRelevance,    // 답변적합도
        int jobFit,            // 직무적합도
        int orgFit,            // 조직적합도
        int specificity,       // 구체성
        int authenticity,      // 진정성/개성
        int aiDetectionRisk,   // AI탐지 위험도 (낮을수록 좋음)
        int logicalStructure,  // 논리적 구조
        int keywordUsage       // 키워드 활용
    ) {}

    public static int calculateTotalScore(Scores s) {
        return (int) Math.round(
            s.answerRelevance * 0.10
            + s.jobFit * 0.20
            + s.orgFit * 0.15
            + s.specificity * 0.20
            + s.authenticity * 0.10
            + (100 - s.aiDetectionRisk) * 0.10
            + s.logicalStructure * 0.05
            + s.keywordUsage * 0.10
        );
    }

    public static String resolveGrade(int totalScore) {
        if (totalScore >= 90) return "S";
        if (totalScore >= 80) return "A";
        if (totalScore >= 70) return "B";
        if (totalScore >= 60) return "C";
        return "D";
    }

    public boolean passThreshold() {
        return totalScore >= 85;
    }

    public static ReviewResult fallback() {
        Scores scores = new Scores(50, 50, 50, 50, 50, 50, 50, 50);
        int total = calculateTotalScore(scores);
        return new ReviewResult(
            scores, total, resolveGrade(total),
            java.util.List.of("검토 파싱 실패로 기본값 적용"),
            java.util.List.of("재검토 필요"),
            "검토 결과 파싱에 실패하여 기본 점수가 적용되었습니다.",
            "{}"
        );
    }
}
