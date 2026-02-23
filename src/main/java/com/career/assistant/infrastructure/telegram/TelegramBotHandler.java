package com.career.assistant.infrastructure.telegram;

import com.career.assistant.application.CoverLetterFacade;
import com.career.assistant.domain.coverletter.CoverLetter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
public class TelegramBotHandler extends TelegramLongPollingBot {

    private final CoverLetterFacade coverLetterFacade;
    private final String chatId;

    public TelegramBotHandler(
        CoverLetterFacade coverLetterFacade,
        @Value("${telegram.bot-token}") String botToken,
        @Value("${telegram.chat-id}") String chatId
    ) {
        super(botToken);
        this.coverLetterFacade = coverLetterFacade;
        this.chatId = chatId;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String text = update.getMessage().getText().trim();

        if (isUrl(text)) {
            handleJobUrl(text);
        } else {
            sendMessage("URL을 보내주시면 자소서 초안을 생성해드립니다!\n예: https://www.wanted.co.kr/...");
        }
    }

    private void handleJobUrl(String url) {
        sendMessage("공고를 분석 중입니다... ⏳");
        try {
            CoverLetter coverLetter = coverLetterFacade.generateFromUrl(url);
            sendMessage("✅ 자소서 초안이 완성됐습니다!\n\n" + coverLetter.getContent());
        } catch (Exception e) {
            log.error("자소서 생성 실패", e);
            sendMessage("❌ 공고 분석에 실패했습니다. URL을 확인해주세요.\n" + e.getMessage());
        }
    }

    public void sendMessage(String text) {
        SendMessage message = SendMessage.builder()
            .chatId(chatId)
            .text(text)
            .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("텔레그램 메시지 전송 실패", e);
        }
    }

    private boolean isUrl(String text) {
        return text.startsWith("http://") || text.startsWith("https://");
    }

    @Override
    public String getBotUsername() {
        return "CareerAssistantBot";
    }
}
