package com.career.assistant.infrastructure.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
public class ClaudeAdapter implements AiPort {

    private final WebClient webClient;
    private final String apiKey;
    private final String modelName;

    public ClaudeAdapter(
            WebClient webClient,
            @Value("${ai.claude.api-key}") String apiKey,
            @Value("${ai.claude.sonnet-model}") String modelName) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    @Override
    public String generate(String prompt) {
        Map<String, Object> requestBody = Map.of(
            "model", modelName,
            "max_tokens", 2048,
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) webClient.post()
            .uri("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        return (String) content.get(0).get("text");
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}
