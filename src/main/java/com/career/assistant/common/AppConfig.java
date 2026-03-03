package com.career.assistant.common;

import com.career.assistant.infrastructure.ai.ClaudeAdapter;
import com.career.assistant.infrastructure.telegram.TelegramBotHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
public class AppConfig {

    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBotHandler botHandler) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(botHandler);
        log.info("텔레그램 봇 등록 완료");
        return api;
    }

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
