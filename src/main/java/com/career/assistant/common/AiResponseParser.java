package com.career.assistant.common;

public final class AiResponseParser {

    private AiResponseParser() {}

    /**
     * AI 응답에서 JSON 블록을 추출한다.
     * 마크다운 코드펜스 제거 후 가장 바깥 { ... } 블록을 반환한다.
     */
    public static String extractJson(String response) {
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
}
