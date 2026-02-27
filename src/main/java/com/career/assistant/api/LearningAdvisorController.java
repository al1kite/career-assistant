package com.career.assistant.api;

import com.career.assistant.application.github.BriefingService;
import com.career.assistant.application.github.LearningAdvisor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@Tag(name = "Learning Advisor", description = "AI 학습 공백 분석 및 맞춤 추천 API")
@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningAdvisorController {

    private final LearningAdvisor learningAdvisor;
    private final BriefingService briefingService;

    @Operation(summary = "학습 공백 분석 + 맞춤 학습 추천",
        description = "GitHub 활동 데이터를 Claude AI가 분석하여 학습 공백, 오늘 할 일, 코테 문제, CS 퀴즈, 블로그 주제를 추천합니다. "
            + "사전 조건: 먼저 POST /api/github/sync를 호출하여 GitHub 활동 데이터를 동기화해야 합니다. "
            + "AI 분석 특성상 응답까지 수 초~수십 초 소요될 수 있습니다.")
    @GetMapping("/recommendation")
    public ResponseEntity<?> getRecommendation() {
        try {
            return ResponseEntity.ok(learningAdvisor.analyze());
        } catch (LearningAdvisor.NoActivityDataException e) {
            log.warn("Learning recommendation requested but no activity data: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                Map.of("error", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Learning recommendation failed", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "AI 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
            );
        }
    }

    @Operation(summary = "아침 브리핑 수동 트리거",
        description = "새벽 6시 스케줄러와 동일한 전체 흐름을 즉시 실행합니다. "
            + "GitHub 동기화 → AI 분석 → 텔레그램 브리핑/퀴즈/코테 전송 → 블로그/CS 글 생성 전송. "
            + "AI 글 생성 포함으로 1~2분 소요될 수 있습니다.")
    @PostMapping("/briefing")
    public ResponseEntity<?> triggerBriefing() {
        try {
            briefingService.executeBriefing();
            return ResponseEntity.ok(Map.of("message", "아침 브리핑이 텔레그램으로 전송되었습니다."));
        } catch (Exception e) {
            log.error("Manual briefing trigger failed", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "브리핑 실행 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
            );
        }
    }
}
