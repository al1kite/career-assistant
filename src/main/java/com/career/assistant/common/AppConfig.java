package com.career.assistant.common;

import com.career.assistant.infrastructure.ai.ClaudeAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean("githubWebClient")
    public WebClient githubWebClient(@Value("${github.token:}") String token) {
        WebClient.Builder builder = WebClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader("Accept", "application/vnd.github+json");
        if (token != null && !token.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    @Bean("claudeSonnet")
    public ClaudeAdapter claudeSonnet(
        WebClient webClient,
        @Value("${ai.claude.sonnet-model}") String model
    ) {
        return new ClaudeAdapter(webClient, model);
    }

    @Bean("claudeHaiku")
    public ClaudeAdapter claudeHaiku(
        WebClient webClient,
        @Value("${ai.claude.haiku-model}") String model
    ) {
        return new ClaudeAdapter(webClient, model);
    }
}
