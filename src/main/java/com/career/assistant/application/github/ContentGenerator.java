package com.career.assistant.application.github;

import com.career.assistant.infrastructure.ai.AiPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ContentGenerator {

    private final AiPort claude;

    private static final String BLOG_SYSTEM_PROMPT = """
        당신은 주니어 백엔드 개발자의 기술 블로그 작성을 돕는 전문 라이터입니다.
        Jekyll 블로그에 게시할 기술 포스팅을 작성합니다.

        [작성 규칙]
        - 2000~3000자 분량의 완성된 글을 작성하세요.
        - 서론(왜 이 주제인지) → 본론(핵심 개념 설명 + 코드 예시) → 정리(요약 + 다음 학습 방향) 구조
        - 코드 예시는 Java 기반으로 작성
        - 핵심 개념은 면접에서 물어볼 수 있는 수준으로 깊이 있게 설명
        - 마크다운 형식으로 출력 (# 제목, ## 소제목, ```java 코드블록)
        - 실무와 연결되는 예시를 포함
        - 친근하지만 전문적인 어투 (존댓말)
        """;

    private static final String CS_SYSTEM_PROMPT = """
        당신은 CS 전공 지식을 정리하는 전문 라이터입니다.
        기술 면접 대비용 CS 스터디 노트를 작성합니다.

        [작성 규칙]
        - 2000~3000자 분량의 완성된 글을 작성하세요.
        - 개념 정의 → 동작 원리 → 장단점 비교 → 면접 예상 질문 + 모범 답변 구조
        - 핵심 키워드는 **볼드** 처리
        - 비교가 필요한 개념은 표로 정리
        - 마크다운 형식으로 출력
        - 면접관이 기대하는 수준의 깊이로 작성
        - 실무 연관성을 포함 (예: "실제 서비스에서 이 개념이 왜 중요한지")
        """;

    public ContentGenerator(@Qualifier("claudeSonnet") AiPort claude) {
        this.claude = claude;
    }

    public String generateBlogPost(LearningRecommendation.BlogSuggestion topic) {
        if (topic == null || topic.title() == null) {
            log.warn("Blog topic is null, skipping generation");
            return null;
        }

        String userPrompt = """
            아래 주제로 기술 블로그 포스팅을 작성해주세요.

            [제목] %s
            [개요] %s

            완성된 블로그 글을 마크다운으로 출력하세요.
            """.formatted(topic.title(), topic.outline() != null ? topic.outline() : "");

        log.info("Generating blog post: {}", topic.title());
        try {
            String content = claude.generate(BLOG_SYSTEM_PROMPT, userPrompt);
            if (content == null) {
                log.warn("Blog post generation returned null for: {}", topic.title());
                return null;
            }
            log.info("Blog post generated: {} ({}자)", topic.title(), content.length());
            return content;
        } catch (Exception e) {
            log.error("Blog post generation failed: {}", topic.title(), e);
            return null;
        }
    }

    public String generateCsStudyNote(LearningRecommendation.LearningGap gap) {
        if (gap == null || gap.topic() == null) {
            log.warn("CS topic is null, skipping generation");
            return null;
        }

        String userPrompt = """
            아래 CS 주제로 스터디 노트를 작성해주세요.

            [주제] %s
            [배경] %s (경과일: %d일)

            완성된 CS 스터디 노트를 마크다운으로 출력하세요.
            """.formatted(gap.topic(), gap.reason(), gap.gapDays());

        log.info("Generating CS study note: {}", gap.topic());
        try {
            String content = claude.generate(CS_SYSTEM_PROMPT, userPrompt);
            if (content == null) {
                log.warn("CS study note generation returned null for: {}", gap.topic());
                return null;
            }
            log.info("CS study note generated: {} ({}자)", gap.topic(), content.length());
            return content;
        } catch (Exception e) {
            log.error("CS study note generation failed: {}", gap.topic(), e);
            return null;
        }
    }
}
