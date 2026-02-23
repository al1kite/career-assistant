package com.career.assistant.infrastructure.telegram;

import com.career.assistant.application.CoverLetterFacade;
import com.career.assistant.domain.coverletter.CoverLetter;
import java.util.List;
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
        sendMessage("공고를 크롤링 중입니다...");
        sendMessage("회사를 AI로 심층 분석 중입니다... (경쟁사, 채용 배경, 문항별 작성 전략 등)");
        sendMessage("분석 완료 후 자소서를 생성합니다. AI 에이전트가 생성 → 검토 → 개선을 반복합니다. 잠시만 기다려주세요.");
        try {
            List<CoverLetter> coverLetters = coverLetterFacade.generateFromUrl(url);
            if (coverLetters.size() == 1) {
                CoverLetter cl = coverLetters.get(0);
                String scoreInfo = formatScoreInfo(cl);
                sendMessage("자소서가 완성됐습니다!" + scoreInfo + "\n\n" + cl.getContent());
            } else {
                StringBuilder sb = new StringBuilder("자소서가 완성됐습니다! (문항 %d개)\n".formatted(coverLetters.size()));
                for (CoverLetter cl : coverLetters) {
                    if (cl.getQuestionIndex() != null) {
                        sb.append("\n--- 문항 %d ---\n".formatted(cl.getQuestionIndex()));
                        sb.append("[%s]\n".formatted(cl.getQuestionText()));
                    }
                    String scoreInfo = formatScoreInfo(cl);
                    sb.append(scoreInfo).append("\n\n");
                    sb.append(cl.getContent()).append("\n");
                }
                sendMessage(sb.toString());
            }
        } catch (Exception e) {
            log.error("자소서 생성 실패", e);
            sendMessage("공고 분석에 실패했습니다. URL을 확인해주세요.\n" + e.getMessage());
        }
    }

    private String formatScoreInfo(CoverLetter cl) {
        if (cl.getReviewScore() == null) {
            return "";
        }
        int score = cl.getReviewScore();
        String grade = resolveGrade(score);
        return "\n[v%d | %d점 | %s등급]".formatted(cl.getVersion(), score, grade);
    }

    private String resolveGrade(int score) {
        if (score >= 90) return "S";
        if (score >= 80) return "A";
        if (score >= 70) return "B";
        if (score >= 60) return "C";
        return "D";
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
