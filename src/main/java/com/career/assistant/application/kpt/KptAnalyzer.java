package com.career.assistant.application.kpt;

import com.career.assistant.application.github.LearningRecommendation.DailyTask;
import com.career.assistant.domain.kpt.KptRecord;
import com.career.assistant.domain.kpt.KptRecordRepository;
import com.career.assistant.infrastructure.ai.AiPort;
import com.career.assistant.infrastructure.github.GitHubClient;
import com.career.assistant.infrastructure.github.GitHubCommit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KptAnalyzer {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String KPT_SYSTEM_PROMPT = """
        당신은 개발자 학습 코치입니다.
        GitHub 커밋 데이터와 아침 브리핑 추천 태스크를 비교하여 KPT(Keep/Problem/Try)를 분석하세요.

        [분석 기준]
        - Keep: 아침 브리핑 추천 중 실행한 것 + 추천 외 추가로 한 것
        - Problem: 추천했지만 실행하지 못한 것 + 이유 추정
        - Try: 내일 구체적으로 시도할 것 (시간/분량 포함)
        - completionRate: 아침 브리핑 추천 태스크 대비 실제 달성률 (0~100)

        [톤]
        - 비난 금지. 격려 + 구체적 제안
        - "못했다"가 아니라 "내일 하면 된다"
        - 연속 달성일이 있으면 칭찬
        - 커밋이 없어도 격려하되, 내일 구체적 행동을 제안

        [출력: JSON]
        반드시 순수 JSON만 출력하세요. 마크다운 코드블록(```) 없이 JSON만 출력하세요.
        {
            "keep": ["잘한 것1", "잘한 것2"],
            "problem": ["아쉬운 것1", "아쉬운 것2"],
            "try": ["내일 시도할 것1", "내일 시도할 것2"],
            "comment": "격려 한마디",
            "completionRate": 0~100
        }
        """;

    private final AiPort claude;
    private final ObjectMapper objectMapper;
    private final KptRecordRepository kptRecordRepository;
    private final GitHubClient gitHubClient;
    private final DailyTasksHolder dailyTasksHolder;

    @Value("${github.username}")
    private String username;

    @Value("${github.repos.coding-test}")
    private String codingTestRepo;

    @Value("${github.repos.blog}")
    private String blogRepo;

    @Value("${github.repos.cs-study}")
    private String csStudyRepo;

    public KptAnalyzer(
        @Qualifier("claudeHaiku") AiPort claude,
        ObjectMapper objectMapper,
        KptRecordRepository kptRecordRepository,
        GitHubClient gitHubClient,
        DailyTasksHolder dailyTasksHolder
    ) {
        this.claude = claude;
        this.objectMapper = objectMapper;
        this.kptRecordRepository = kptRecordRepository;
        this.gitHubClient = gitHubClient;
        this.dailyTasksHolder = dailyTasksHolder;
    }

    public KptRecord analyze() {
        String todayActivity = fetchTodayActivity();
        List<DailyTask> todayTasks = dailyTasksHolder.get();

        String userPrompt = buildUserPrompt(todayActivity, todayTasks);
        log.info("Requesting KPT analysis from Claude...");
        String response = claude.generate(KPT_SYSTEM_PROMPT, userPrompt);
        log.info("Received KPT analysis response");

        KptResult result = parseResponse(response);

        KptRecord record = KptRecord.of(
            LocalDate.now(KST),
            todayActivity,
            toJson(result.keep()),
            toJson(result.problem()),
            toJson(result.tryActions()),
            result.comment(),
            Math.max(0, Math.min(100, result.completionRate()))
        );

        return kptRecordRepository.save(record);
    }

    private String fetchTodayActivity() {
        ZonedDateTime kstStartOfDay = LocalDate.now(KST).atStartOfDay(KST);
        LocalDateTime sinceUtc = kstStartOfDay.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
        StringBuilder sb = new StringBuilder();

        appendRepoCommits(sb, codingTestRepo, "코딩테스트", sinceUtc);
        appendRepoCommits(sb, blogRepo, "블로그", sinceUtc);
        appendRepoCommits(sb, csStudyRepo, "CS 스터디", sinceUtc);

        return sb.isEmpty() ? "오늘 GitHub 커밋 없음" : sb.toString().strip();
    }

    private void appendRepoCommits(StringBuilder sb, String repo, String label,
                                    LocalDateTime since) {
        List<GitHubCommit> commits = gitHubClient.getCommits(username, repo, since);
        if (commits.isEmpty()) return;

        sb.append("[").append(label).append("]\n");
        for (GitHubCommit commit : commits) {
            String firstLine = commit.getMessage().split("\n")[0];
            sb.append("- ").append(firstLine).append("\n");
        }
        sb.append("\n");
    }

    private String buildUserPrompt(String todayActivity, List<DailyTask> todayTasks) {
        String tasksData = todayTasks.isEmpty()
            ? "오늘 아침 브리핑 데이터 없음"
            : todayTasks.stream()
                .map(t -> "- [%s] %s".formatted(t.category(), t.action()))
                .collect(Collectors.joining("\n"));

        return """
            [오늘 아침 브리핑 추천 태스크]
            %s

            [오늘 GitHub 활동]
            %s

            위 데이터를 비교 분석하여 KPT JSON을 생성해주세요.
            """.formatted(tasksData, todayActivity);
    }

    private KptResult parseResponse(String response) {
        if (response == null || response.isBlank()) {
            log.warn("KPT analysis returned null or blank response");
            throw new RuntimeException("KPT 분석 응답이 비어있습니다.");
        }

        String json = extractJson(response);

        if (json == null) {
            String preview = response.substring(0, Math.min(300, response.length()));
            log.warn("Failed to extract JSON from KPT response. Response preview: {}", preview);
            throw new RuntimeException("KPT 분석 응답에서 유효한 JSON을 추출할 수 없습니다.");
        }

        try {
            return objectMapper.readValue(json, KptResult.class);
        } catch (JsonProcessingException e) {
            String preview = json.substring(0, Math.min(300, json.length()));
            log.warn("Failed to parse KPT JSON: {}. JSON preview: {}", e.getMessage(), preview);
            throw new RuntimeException("KPT 분석 응답 파싱 실패.", e);
        }
    }

    private String extractJson(String response) {
        if (response == null || response.isBlank()) return null;

        String trimmed = response.strip();

        if (trimmed.startsWith("```")) {
            int endIdx = trimmed.lastIndexOf("```");
            if (endIdx > 3) {
                trimmed = trimmed.substring(trimmed.indexOf('\n') + 1, endIdx).strip();
            }
        }

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }

        return null;
    }

    private String toJson(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items != null ? items : List.of());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize list to JSON", e);
            return "[]";
        }
    }
}
