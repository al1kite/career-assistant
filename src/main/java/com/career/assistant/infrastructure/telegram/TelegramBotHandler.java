package com.career.assistant.infrastructure.telegram;

import com.career.assistant.application.CoverLetterFacade;
import com.career.assistant.application.interview.InterviewPrepAnalyzer;
import com.career.assistant.application.interview.InterviewPrepMessageFormatter;
import com.career.assistant.application.interview.InterviewPrepResult;
import com.career.assistant.application.jobcollector.JobCollectorService;
import com.career.assistant.application.kpt.KptMessageFormatter;
import com.career.assistant.domain.coverletter.CoverLetter;
import com.career.assistant.domain.coverletter.CoverLetterRepository;
import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.domain.jobposting.JobPostingRepository;
import com.career.assistant.domain.kpt.KptRecord;
import com.career.assistant.domain.kpt.KptRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final CoverLetterRepository coverLetterRepository;
    private final JobPostingRepository jobPostingRepository;
    private final JobCollectorService jobCollectorService;
    private final KptMessageFormatter kptMessageFormatter;
    private final KptRecordRepository kptRecordRepository;
    private final InterviewPrepAnalyzer interviewPrepAnalyzer;
    private final InterviewPrepMessageFormatter interviewPrepMessageFormatter;
    private final ObjectMapper objectMapper;
    private final String chatId;

    public TelegramBotHandler(
        CoverLetterFacade coverLetterFacade,
        CoverLetterRepository coverLetterRepository,
        JobPostingRepository jobPostingRepository,
        JobCollectorService jobCollectorService,
        KptMessageFormatter kptMessageFormatter,
        KptRecordRepository kptRecordRepository,
        InterviewPrepAnalyzer interviewPrepAnalyzer,
        InterviewPrepMessageFormatter interviewPrepMessageFormatter,
        ObjectMapper objectMapper,
        @Value("${telegram.bot-token}") String botToken,
        @Value("${telegram.chat-id}") String chatId
    ) {
        super(botToken);
        this.coverLetterFacade = coverLetterFacade;
        this.coverLetterRepository = coverLetterRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.jobCollectorService = jobCollectorService;
        this.kptMessageFormatter = kptMessageFormatter;
        this.kptRecordRepository = kptRecordRepository;
        this.interviewPrepAnalyzer = interviewPrepAnalyzer;
        this.interviewPrepMessageFormatter = interviewPrepMessageFormatter;
        this.objectMapper = objectMapper;
        this.chatId = chatId;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String incomingChatId = update.getMessage().getChatId().toString();
        if (!chatId.equals(incomingChatId)) {
            log.warn("허용되지 않은 chatId 접근: {}", incomingChatId);
            return;
        }

        String text = update.getMessage().getText().trim();
        String command = text.split("\\s+")[0];
        log.info("커맨드 수신: {} (chatId: {})", command, incomingChatId);
        log.debug("메시지 전문: {}", text);

        try {
            if (text.startsWith("/start")) {
                handleStartCommand();
            } else if (text.startsWith("/공고")) {
                handleJobListCommand();
            } else if (text.startsWith("/kpt")) {
                handleKptCommand(text);
            } else if (text.startsWith("/면접")) {
                handleInterviewCommand(text);
            } else if (text.startsWith("/개선")) {
                handleImproveCommand(text);
            } else if (text.startsWith("/비교")) {
                handleCompareCommand(text);
            } else if (text.startsWith("/기록")) {
                handleHistoryCommand(text);
            } else if (isUrl(text)) {
                handleJobUrl(text);
            } else {
                handleStartCommand();
            }
        } catch (Exception e) {
            log.error("메시지 처리 중 오류 발생: {}", command, e);
            sendMessage("오류가 발생했습니다: " + e.getMessage());
        }
    }

    // ── 시작/도움말 ──────────────────────────────────────────

    private void handleStartCommand() {
        sendMessage("Career Assistant Bot\n"
            + "════════════════════\n"
            + "취업 준비를 도와주는 AI 봇입니다.\n\n"
            + "[자동 기능]\n"
            + "  매일 06:00 — 아침 브리핑 (학습 공백 분석 + 코테/CS/블로그 추천)\n"
            + "  매일 08:00 — 채용공고 자동 수집 (자소설닷컴/원티드/사람인)\n"
            + "  매일 09:00 — 마감 임박 공고 알림 (3일 이내)\n"
            + "  매일 22:00 — KPT 회고 (GitHub 커밋 기반 자동 분석)\n\n"
            + "[채용공고]\n"
            + "  /공고 — 수집된 활성 공고 목록\n"
            + "  채용공고 URL 전송 — AI 자소서 생성 (크롤링 → 분석 → 생성 → 검토)\n\n"
            + "[자소서]\n"
            + "  /기록 — 최근 자소서 요약 (최근 5개 회사)\n"
            + "  /기록 {회사명} — 해당 회사 문항별 점수\n"
            + "  /비교 {회사명} {문항번호} — 버전별 점수 변화 비교\n"
            + "  /개선 {회사명} — 기존 자소서 추가 개선 (리뷰+개선 재실행)\n\n"
            + "[면접 준비]\n"
            + "  /면접 {회사명} — 면접 예상 질문 + 답변 가이드 생성\n\n"
            + "[KPT 회고]\n"
            + "  /kpt — 최근 7일 KPT 기록\n"
            + "  /kpt 주간 — 이번 주 KPT 요약 (달성률 추이)\n\n"
            + "채용공고 URL을 보내주시면 바로 시작합니다!");
    }

    // ── 채용공고 ──────────────────────────────────────────

    private void handleJobListCommand() {
        sendMessage(jobCollectorService.getActivePostingsSummary());
    }

    // ── KPT 회고 ──────────────────────────────────────────

    private void handleKptCommand(String text) {
        String arg = text.replaceFirst("/kpt", "").trim();

        if ("주간".equals(arg)) {
            handleWeeklyKpt();
        } else {
            handleRecentKpt();
        }
    }

    private void handleRecentKpt() {
        List<KptRecord> records = kptRecordRepository.findTop7ByOrderByDateDesc();
        sendMessage(kptMessageFormatter.formatHistory(records));
    }

    private void handleWeeklyKpt() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        List<KptRecord> records = kptRecordRepository.findByDateBetweenOrderByDateDesc(weekStart, today);
        sendMessage(kptMessageFormatter.formatWeeklySummary(records));
    }

    // ── 면접 준비 ──────────────────────────────────────────

    private void handleInterviewCommand(String text) {
        String companyName = text.replaceFirst("/면접", "").trim();

        if (companyName.isEmpty()) {
            sendMessage("사용법: /면접 {회사명}\n예: /면접 카카오");
            return;
        }

        List<JobPosting> matched = jobPostingRepository.findByCompanyNameContaining(companyName);
        if (matched.isEmpty()) {
            sendMessage("'%s' 회사의 공고를 찾을 수 없습니다.\n채용공고 URL을 먼저 보내 자소서를 생성해주세요.".formatted(companyName));
            return;
        }

        // 분석 데이터가 있는 공고 우선, 없으면 최신 공고
        JobPosting jobPosting = matched.stream()
            .filter(jp -> jp.getCompanyAnalysis() != null && !jp.getCompanyAnalysis().isBlank())
            .max(java.util.Comparator.comparing(JobPosting::getCreatedAt))
            .orElseGet(() -> matched.stream()
                .max(java.util.Comparator.comparing(JobPosting::getCreatedAt))
                .orElse(matched.get(0)));

        if (jobPosting.getCompanyAnalysis() == null || jobPosting.getCompanyAnalysis().isBlank()) {
            sendMessage("'%s' 회사의 분석 데이터가 없습니다.\n채용공고 URL을 먼저 보내 자소서를 생성해주세요.".formatted(jobPosting.getCompanyName()));
            return;
        }

        sendMessage("면접 예상 질문 생성 중... (회사 분석 기반)");

        try {
            InterviewPrepResult result = interviewPrepAnalyzer.analyze(jobPosting);
            String message = interviewPrepMessageFormatter.format(jobPosting.getCompanyName(), result);
            sendMessage(message);
        } catch (Exception e) {
            log.error("면접 준비 생성 실패 - {}", jobPosting.getCompanyName(), e);
            sendMessage("면접 준비 가이드 생성에 실패했습니다: " + e.getMessage());
        }
    }

    // ── 자소서 ──────────────────────────────────────────

    private void handleJobUrl(String url) {
        boolean alreadyProcessed = jobPostingRepository.existsByUrl(url);
        String companyName = null;

        if (alreadyProcessed) {
            JobPosting existing = jobPostingRepository.findByUrl(url).orElse(null);
            if (existing != null && !existing.needsCrawling()) {
                List<CoverLetter> existingLetters = coverLetterRepository.findByJobPostingId(existing.getId());
                if (!existingLetters.isEmpty()) {
                    companyName = existing.getCompanyName();
                }
            }
        }

        if (!alreadyProcessed) {
            sendMessage("공고를 크롤링 중입니다...");
            sendMessage("회사를 AI로 심층 분석 중입니다... (경쟁사, 채용 배경, 문항별 작성 전략 등)");
            sendMessage("분석 완료 후 자소서를 생성합니다. AI 에이전트가 생성 → 검토 → 개선을 반복합니다. 잠시만 기다려주세요.");
        }

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

            if (companyName != null) {
                sendMessage("이미 생성된 자소서입니다. 추가 개선을 원하시면 /개선 %s 을 입력하세요.".formatted(companyName));
            }
        } catch (Exception e) {
            log.error("자소서 생성 실패", e);
            sendMessage("공고 분석에 실패했습니다. URL을 확인해주세요.\n" + e.getMessage());
        }
    }

    private void handleImproveCommand(String text) {
        String companyName = text.replaceFirst("/개선", "").trim();

        if (companyName.isEmpty()) {
            sendMessage("사용법: /개선 {회사명}\n예: /개선 카카오");
            return;
        }

        List<JobPosting> matched = jobPostingRepository.findByCompanyNameContaining(companyName);
        if (matched.isEmpty()) {
            sendMessage("'%s' 회사의 공고를 찾을 수 없습니다.".formatted(companyName));
            return;
        }

        JobPosting jp = matched.stream()
            .max(java.util.Comparator.comparing(JobPosting::getCreatedAt))
            .orElse(matched.get(0));

        List<CoverLetter> existing = coverLetterRepository.findByJobPostingId(jp.getId());
        if (existing.isEmpty()) {
            sendMessage("'%s' 자소서가 아직 생성되지 않았습니다. 채용공고 URL을 먼저 보내주세요.".formatted(jp.getCompanyName()));
            return;
        }

        Map<Integer, CoverLetter> beforeLatest = CoverLetterFacade.extractLatestByQuestion(existing);
        Map<Integer, Integer> beforeScores = new LinkedHashMap<>();
        for (var entry : beforeLatest.entrySet()) {
            beforeScores.put(entry.getKey(), entry.getValue().getReviewScore() != null ? entry.getValue().getReviewScore() : 0);
        }

        sendMessage("'%s' 자소서 추가 개선을 시작합니다. AI 에이전트가 검토 → 개선을 반복합니다...".formatted(jp.getCompanyName()));

        try {
            List<CoverLetter> improved = coverLetterFacade.improveExisting(jp.getId());

            StringBuilder sb = new StringBuilder("'%s' 자소서 개선 완료!\n\n".formatted(jp.getCompanyName()));
            for (CoverLetter cl : improved) {
                int qIdx = cl.getQuestionIndex() != null ? cl.getQuestionIndex() : 0;
                int before = beforeScores.getOrDefault(qIdx, 0);
                int after = cl.getReviewScore() != null ? cl.getReviewScore() : 0;
                int diff = after - before;
                String arrow = diff > 0 ? "+" + diff : String.valueOf(diff);
                String qText = cl.getQuestionText() != null ? cl.getQuestionText() : "(단일 문항)";
                if (qText.length() > 30) qText = qText.substring(0, 30) + "...";

                sb.append("문항%d: %s\n".formatted(qIdx, qText));
                sb.append("  %d점 → %d점 (%s) | v%d\n\n".formatted(before, after, arrow, cl.getVersion()));
            }
            sendMessage(sb.toString());
        } catch (Exception e) {
            log.error("자소서 개선 실패 - {}", jp.getCompanyName(), e);
            sendMessage("자소서 개선에 실패했습니다: " + e.getMessage());
        }
    }

    private void handleHistoryCommand(String text) {
        String arg = text.replaceFirst("/기록", "").trim();

        if (arg.isEmpty()) {
            handleRecentHistory();
        } else {
            handleCompanyHistory(arg);
        }
    }

    private void handleRecentHistory() {
        List<JobPosting> allPostings = jobPostingRepository.findAll();
        if (allPostings.isEmpty()) {
            sendMessage("저장된 자소서가 없습니다.");
            return;
        }

        allPostings.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        List<JobPosting> recent = allPostings.subList(0, Math.min(5, allPostings.size()));

        List<Long> ids = recent.stream().map(JobPosting::getId).toList();
        List<CoverLetter> letters = coverLetterRepository.findByJobPostingIdIn(ids);
        Map<Long, List<CoverLetter>> byPosting = letters.stream()
            .collect(Collectors.groupingBy(cl -> cl.getJobPosting().getId()));

        StringBuilder sb = new StringBuilder();
        for (JobPosting jp : recent) {
            List<CoverLetter> cls = byPosting.getOrDefault(jp.getId(), List.of());
            if (cls.isEmpty()) continue;

            Map<Integer, CoverLetter> latest = extractLatestByQuestion(cls);

            int avgScore = (int) latest.values().stream()
                .filter(cl -> cl.getReviewScore() != null)
                .mapToInt(CoverLetter::getReviewScore)
                .average().orElse(0);

            sb.append("  %s — 문항 %d개 | 평균 %d점 (%s)\n".formatted(
                jp.getCompanyName() != null ? jp.getCompanyName() : "(회사명 없음)",
                latest.size(),
                avgScore,
                resolveGrade(avgScore)
            ));
        }

        if (sb.isEmpty()) {
            sendMessage("저장된 자소서가 없습니다.");
        } else {
            sendMessage("최근 자소서 기록\n\n" + sb);
        }
    }

    private void handleCompanyHistory(String companyName) {
        List<JobPosting> matched = jobPostingRepository.findByCompanyNameContaining(companyName);
        if (matched.isEmpty()) {
            sendMessage("'%s' 회사의 자소서를 찾을 수 없습니다.".formatted(companyName));
            return;
        }

        JobPosting jp = matched.get(0);
        List<CoverLetter> cls = coverLetterRepository.findByJobPostingId(jp.getId());
        if (cls.isEmpty()) {
            sendMessage("'%s' 자소서가 아직 생성되지 않았습니다.".formatted(jp.getCompanyName()));
            return;
        }

        Map<Integer, CoverLetter> latest = extractLatestByQuestion(cls);

        StringBuilder sb = new StringBuilder("%s 자소서 현황\n\n".formatted(jp.getCompanyName()));
        for (var entry : latest.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            CoverLetter cl = entry.getValue();
            String qText = cl.getQuestionText() != null ? cl.getQuestionText() : "(단일 문항)";
            if (qText.length() > 30) qText = qText.substring(0, 30) + "...";
            int score = cl.getReviewScore() != null ? cl.getReviewScore() : 0;
            sb.append("문항%d: %s\n → v%d | %d점 (%s)\n\n".formatted(
                entry.getKey(), qText, cl.getVersion(), score, resolveGrade(score)
            ));
        }
        sendMessage(sb.toString());
    }

    private void handleCompareCommand(String text) {
        String arg = text.replaceFirst("/비교", "").trim();
        String[] parts = arg.split("\\s+");

        if (parts.length < 2) {
            sendMessage("사용법: /비교 {회사명} {문항번호}\n예: /비교 카카오 1");
            return;
        }

        int questionIndex;
        try {
            questionIndex = Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            sendMessage("사용법: /비교 {회사명} {문항번호}\n예: /비교 카카오 1");
            return;
        }
        String companyName = String.join(" ", Arrays.copyOf(parts, parts.length - 1));

        List<JobPosting> matched = jobPostingRepository.findByCompanyNameContaining(companyName);
        if (matched.isEmpty()) {
            sendMessage("'%s' 회사의 자소서를 찾을 수 없습니다.".formatted(companyName));
            return;
        }

        JobPosting jp = matched.get(0);
        List<CoverLetter> versions = coverLetterRepository
            .findByJobPostingIdAndQuestionIndexOrderByVersionAsc(jp.getId(), questionIndex);

        if (versions.isEmpty()) {
            sendMessage("'%s' 문항 %d번의 자소서를 찾을 수 없습니다.".formatted(jp.getCompanyName(), questionIndex));
            return;
        }

        StringBuilder sb = new StringBuilder("%s — 문항 %d 버전 비교\n\n".formatted(jp.getCompanyName(), questionIndex));

        for (CoverLetter v : versions) {
            int score = v.getReviewScore() != null ? v.getReviewScore() : 0;
            sb.append("v%d: %d점 (%s)".formatted(v.getVersion(), score, resolveGrade(score)));

            if (v.getFeedback() != null && !v.getFeedback().isBlank()) {
                try {
                    JsonNode root = objectMapper.readTree(v.getFeedback());
                    JsonNode scores = root.get("scores");
                    if (scores != null) {
                        sb.append("\n  직무적합도:%d 구체성:%d 조직적합도:%d".formatted(
                            scores.path("jobFit").asInt(),
                            scores.path("specificity").asInt(),
                            scores.path("orgFit").asInt()
                        ));
                    }
                } catch (Exception e) {
                    // 파싱 실패시 무시
                }
            }
            sb.append("\n");
        }

        CoverLetter first = versions.get(0);
        CoverLetter last = versions.get(versions.size() - 1);
        int firstScore = first.getReviewScore() != null ? first.getReviewScore() : 0;
        int lastScore = last.getReviewScore() != null ? last.getReviewScore() : 0;
        int diff = lastScore - firstScore;
        String arrow = diff > 0 ? "+" + diff : String.valueOf(diff);
        sb.append("\nv%d→v%d: %d점 → %d점 (%s)".formatted(first.getVersion(), last.getVersion(), firstScore, lastScore, arrow));

        sendMessage(sb.toString());
    }

    // ── 공통 유틸 ──────────────────────────────────────────

    private static Map<Integer, CoverLetter> extractLatestByQuestion(List<CoverLetter> letters) {
        return CoverLetterFacade.extractLatestByQuestion(letters);
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

    private static final int MAX_MESSAGE_LENGTH = 4096;

    public void sendMessage(String text) {
        if (text == null || text.isBlank()) return;

        if (text.length() <= MAX_MESSAGE_LENGTH) {
            doSend(text);
            return;
        }

        int offset = 0;
        while (offset < text.length()) {
            int end = Math.min(offset + MAX_MESSAGE_LENGTH, text.length());
            if (end < text.length()) {
                int lastNewline = text.lastIndexOf('\n', end);
                if (lastNewline > offset) {
                    end = lastNewline + 1;
                }
            }
            doSend(text.substring(offset, end));
            offset = end;
        }
    }

    private void doSend(String text) {
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
