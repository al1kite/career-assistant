package com.career.assistant.infrastructure.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
public class ClaudeAdapter implements AiPort {

    private static final String DEFAULT_SYSTEM_PROMPT = """
        당신은 대한민국 최고의 자소서 전문가입니다. 대기업·금융·IT 채용위원을 20년간 자문해온 HR 컨설턴트로,
        당신이 코칭한 지원자 중 92%가 서류 전형을 통과했습니다.

        [핵심 철학]
        "이 회사가 아니면 안 되는 사람"을 만든다.
        회사명을 바꾸면 쓸 수 없는, 오직 이 회사에만 해당하는 자소서를 쓴다.

        [출력 규칙]
        순수 텍스트만 출력. 마크다운 절대 금지. 단락 사이 빈 줄 하나로 구분.
        자소서 본문만 출력. 앞뒤 인사말, 설명, 메타 코멘트 금지.

        모든 세부 작성 지침은 각 요청의 본문에 포함되어 있습니다. 본문의 지침을 충실히 따르세요.""";

    private final WebClient webClient;
    private final String modelName;

    @Value("${ai.claude.api-key}")
    private String apiKey;

    public ClaudeAdapter(WebClient webClient, String modelName) {
        this.webClient = webClient;
        this.modelName = modelName;
    }

    @Override
    public String generate(String prompt) {
        return callClaude(DEFAULT_SYSTEM_PROMPT, prompt);
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return callClaude(systemPrompt, userPrompt);
    }

    private String callClaude(String systemPrompt, String userPrompt) {
        Map<String, Object> systemBlock = Map.of(
            "type", "text",
            "text", systemPrompt,
            "cache_control", Map.of("type", "ephemeral")
        );

        Map<String, Object> requestBody = Map.of(
            "model", modelName,
            "max_tokens", 4096,
            "system", List.of(systemBlock),
            "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) webClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("anthropic-beta", "prompt-caching-2024-07-31")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(120))
                .block();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            return (String) content.get(0).get("text");
        } catch (WebClientResponseException e) {
            log.error("Claude API 호출 실패 [{}] - 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Claude API 호출 실패: " + e.getResponseBodyAsString(), e);
        }
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}
