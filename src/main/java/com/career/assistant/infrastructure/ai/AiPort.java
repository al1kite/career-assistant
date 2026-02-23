package com.career.assistant.infrastructure.ai;

public interface AiPort {
    String generate(String prompt);
    String getModelName();
}
