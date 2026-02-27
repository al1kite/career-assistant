package com.career.assistant.api;

import com.career.assistant.application.github.LearningAdvisor;
import com.career.assistant.application.github.LearningRecommendation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
            log.error("Learning recommendation failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(
                Map.of("error", "AI 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
            );
        }
    }
}
