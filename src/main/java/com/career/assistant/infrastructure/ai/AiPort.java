package com.career.assistant.infrastructure.ai;

public interface AiPort {
    String generate(String prompt);

    default String generate(String systemPrompt, String userPrompt) {
        return generate(userPrompt);
    }

    /** cachedContext를 별도 캐시 블록으로 분리하여 동일 공고 내 반복 호출 시 입력 토큰 절감 */
    default String generate(String systemPrompt, String cachedContext, String userPrompt) {
        return generate(systemPrompt, cachedContext + "\n\n" + userPrompt);
    }

    /** 기본 시스템 프롬프트 + 캐시 컨텍스트 + 유저 프롬프트 */
    default String generateWithContext(String cachedContext, String userPrompt) {
        return generate(cachedContext + "\n\n" + userPrompt);
    }

    String getModelName();
}
