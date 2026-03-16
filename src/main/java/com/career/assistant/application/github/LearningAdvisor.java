package com.career.assistant.application.github;

import com.career.assistant.domain.github.GitHubActivity;
import com.career.assistant.infrastructure.ai.AiPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LearningAdvisor {

    private final AiPort claude;
    private final GitHubAnalyzer gitHubAnalyzer;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        당신은 개발자 취업 준비 코치입니다.
        GitHub 커밋 데이터를 분석하여 학습 공백을 찾고, 구체적인 행동을 추천합니다.

        [판단 기준]
        - 14일 이상 공백: "긴급" — 즉시 학습 필요
        - 7~14일 공백: "주의" — 이번 주 안에 학습
        - 7일 이내: "양호" — 현재 페이스 유지
        - 특정 유형 편중: "균형" — 약한 유형 보강

        [추천 원칙]
        - 추상적 조언 금지 ("DP 공부하세요" → X)
        - 구체적 행동 ("백준 1149 RGB거리 풀기, 2차원 DP 테이블 연습" → O)
        - 난이도 점진적 상승
        - 하루 학습량: 코테 1문제 + CS 1주제 + 블로그 1초안

        [출력 형식]
        반드시 순수 JSON만 출력하세요. 마크다운 코드블록(```) 없이 JSON만 출력하세요.
        {
          "gaps": [
            {"topic": "주제명", "gapDays": 숫자, "severity": "긴급|주의|양호", "reason": "구체적 사유"}
          ],
          "todayTasks": [
            {"category": "코테|CS|블로그", "action": "구체적 행동", "reason": "추천 이유"}
          ],
          "problems": [
            {"platform": "백준|프로그래머스", "number": "문제번호", "title": "문제명", "type": "알고리즘유형", "difficulty": "난이도", "url": "문제URL"}
          ],
          "quizzes": [
            {"topic": "CS주제", "question": "질문", "options": ["선택지1","선택지2","선택지3","선택지4"], "answer": 정답인덱스, "explanation": "해설"}
          ],
          "blogTopic": {"title": "블로그 제목", "outline": "개요"}
        }
        gaps는 최대 3개, todayTasks 3개, problems 2개, quizzes 3개, blogTopic 1개를 생성하세요.
        """;

    public LearningAdvisor(
        @Qualifier("claudeHaiku") AiPort claude,
        GitHubAnalyzer gitHubAnalyzer,
        ObjectMapper objectMapper
    ) {
        this.claude = claude;
        this.gitHubAnalyzer = gitHubAnalyzer;
        this.objectMapper = objectMapper;
    }

    public LearningRecommendation analyze() {
        List<GitHubActivity> activities = gitHubAnalyzer.getActivities();

        if (activities.isEmpty()) {
            log.warn("No GitHub activities found. Run sync first.");
            throw new NoActivityDataException("GitHub 활동 데이터가 없습니다. 먼저 /api/github/sync를 호출하세요.");
        }

        String userPrompt = buildUserPrompt(activities);
        log.info("Requesting learning analysis from Claude...");
        String response = claude.generate(SYSTEM_PROMPT, userPrompt);
        log.info("Received learning analysis response");

        return parseResponse(response);
    }

    private String buildUserPrompt(List<GitHubActivity> activities) {
        String activityData = activities.stream()
            .map(a -> String.format("- [%s] %s | 마지막 커밋: %s | 커밋 수: %d | 경과일: %d일 | 상태: %s",
                a.getRepoName(),
                a.getTopic(),
                a.getLastCommitAt() != null ? a.getLastCommitAt().toLocalDate().toString() : "없음",
                a.getCommitCount(),
                a.getGapDays(),
                a.getStatus().name()))
            .collect(Collectors.joining("\n"));

        return """
            [학습 현황 데이터]
            %s

            위 데이터를 분석하여 학습 공백, 오늘 할 일, 코테 추천 문제, CS 퀴즈, 블로그 주제를 JSON으로 추천해주세요.
            """.formatted(activityData);
    }

    private LearningRecommendation parseResponse(String response) {
        String json = extractJson(response);

        if (json == null) {
            log.warn("Failed to extract JSON from AI response. Response preview: {}",
                response.substring(0, Math.min(300, response.length())));
            throw new RuntimeException("AI 응답에서 유효한 JSON을 추출할 수 없습니다. 다시 시도해주세요.");
        }

        try {
            return objectMapper.readValue(json, LearningRecommendation.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse learning recommendation JSON: {}. JSON preview: {}",
                e.getMessage(), json.substring(0, Math.min(300, json.length())));
            throw new RuntimeException("AI 응답 파싱 실패. 다시 시도해주세요.", e);
        }
    }

    private String extractJson(String response) {
        if (response == null || response.isBlank()) return null;

        String trimmed = response.strip();

        // 마크다운 코드블록 제거
        if (trimmed.startsWith("```")) {
            int endIdx = trimmed.lastIndexOf("```");
            if (endIdx > 3) {
                trimmed = trimmed.substring(trimmed.indexOf('\n') + 1, endIdx).strip();
            }
        }

        // 가장 바깥 { ... } 블록 추출
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }

        return null;
    }

    public static class NoActivityDataException extends RuntimeException {
        public NoActivityDataException(String message) {
            super(message);
        }
    }
}
