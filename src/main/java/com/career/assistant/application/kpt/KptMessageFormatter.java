package com.career.assistant.application.kpt;

import com.career.assistant.domain.kpt.KptRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class KptMessageFormatter {

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("MM/dd (E)", Locale.KOREAN);

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public String formatResult(KptRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append("오늘의 KPT\n");
        sb.append("────────────\n\n");

        List<String> keeps = parseJsonList(record.getKeepItems());
        if (!keeps.isEmpty()) {
            sb.append("Keep (잘한 것)\n");
            for (String item : keeps) {
                sb.append("  * ").append(item).append("\n");
            }
            sb.append("\n");
        }

        List<String> problems = parseJsonList(record.getProblemItems());
        if (!problems.isEmpty()) {
            sb.append("Problem (아쉬운 것)\n");
            for (String item : problems) {
                sb.append("  * ").append(item).append("\n");
            }
            sb.append("\n");
        }

        List<String> tries = parseJsonList(record.getTryItems());
        if (!tries.isEmpty()) {
            sb.append("Try (내일 시도할 것)\n");
            for (String item : tries) {
                sb.append("  * ").append(item).append("\n");
            }
            sb.append("\n");
        }

        sb.append("달성률: ").append(record.getCompletionRate()).append("%\n");

        if (record.getAiComment() != null && !record.getAiComment().isBlank()) {
            sb.append("\n").append(record.getAiComment());
        }

        return sb.toString();
    }

    public String formatHistory(List<KptRecord> records) {
        if (records.isEmpty()) {
            return "저장된 KPT 기록이 없습니다.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("최근 KPT 기록\n");
        sb.append("════════════════════\n\n");

        for (KptRecord record : records) {
            sb.append(record.getDate().format(DATE_FMT))
              .append(" — 달성률 ").append(record.getCompletionRate()).append("%\n");

            List<String> keeps = parseJsonList(record.getKeepItems());
            if (!keeps.isEmpty()) {
                sb.append("  Keep: ").append(String.join(", ", keeps)).append("\n");
            }

            List<String> problems = parseJsonList(record.getProblemItems());
            if (!problems.isEmpty()) {
                sb.append("  Problem: ").append(String.join(", ", problems)).append("\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    public String formatWeeklySummary(List<KptRecord> records) {
        if (records.isEmpty()) {
            return "이번 주 KPT 기록이 없습니다.";
        }

        int totalDays = records.size();
        int avgRate = (int) records.stream()
            .mapToInt(KptRecord::getCompletionRate)
            .average().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append("이번 주 KPT 요약\n");
        sb.append("════════════════════\n\n");
        sb.append("회고 일수: ").append(totalDays).append("일\n");
        sb.append("평균 달성률: ").append(avgRate).append("%\n\n");

        sb.append("일별 추이\n");
        for (KptRecord record : records) {
            int barLength = record.getCompletionRate() / 10;
            String bar = "|".repeat(Math.max(0, barLength));
            sb.append("  ").append(record.getDate().format(DATE_FMT))
              .append(" ").append(bar)
              .append(" ").append(record.getCompletionRate()).append("%\n");
        }

        return sb.toString();
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON list: {}", json);
            return List.of();
        }
    }
}
