package com.career.assistant.api;

import com.career.assistant.application.github.LearningAdvisor;
import com.career.assistant.application.github.LearningRecommendation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Learning Advisor", description = "AI 학습 공백 분석 및 맞춤 추천 API")
@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningAdvisorController {

    private final LearningAdvisor learningAdvisor;

    @Operation(summary = "학습 공백 분석 + 맞춤 학습 추천",
        description = "GitHub 활동 데이터를 Claude AI가 분석하여 학습 공백, 오늘 할 일, 코테 문제, CS 퀴즈, 블로그 주제를 추천합니다.")
    @GetMapping("/recommendation")
    public ResponseEntity<LearningRecommendation> getRecommendation() {
        return ResponseEntity.ok(learningAdvisor.analyze());
    }
}
