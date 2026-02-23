package com.career.assistant.infrastructure.ai;

public interface AiPort {
    String generate(String prompt);

    default String generate(String systemPrompt, String userPrompt) {
        return generate(userPrompt);
    }

    String getModelName();
}
