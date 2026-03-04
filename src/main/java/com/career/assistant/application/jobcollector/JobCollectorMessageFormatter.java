package com.career.assistant.application.jobcollector;

import com.career.assistant.domain.jobposting.JobPosting;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class JobCollectorMessageFormatter {

    public String formatNewPostings(List<JobPosting> postings) {
        if (postings.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("신규 채용공고 %d건\n".formatted(postings.size()));
        sb.append("════════════════════\n\n");

        for (JobPosting p : postings) {
            sb.append("▸ ").append(p.getCompanyName());
            if (p.getJobDescription() != null && !p.getJobDescription().isBlank()) {
                sb.append(" — ").append(truncate(p.getJobDescription(), 40));
            }
            sb.append("\n");
            if (p.getDeadline() != null) {
                sb.append("  마감: ").append(p.getDeadline()).append(daysLeft(p.getDeadline())).append("\n");
            }
            sb.append("  ").append(p.getUrl()).append("\n\n");
        }

        sb.append("채용공고 URL을 보내주시면 자소서를 생성합니다!");
        return sb.toString();
    }

    public String formatDeadlineAlerts(List<JobPosting> postings) {
        if (postings.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("마감 임박 공고 알림\n");
        sb.append("════════════════════\n\n");

        for (JobPosting p : postings) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), p.getDeadline());
            String urgency = days == 0 ? "오늘 마감!" : days == 1 ? "내일 마감!" : days + "일 남음";

            sb.append("⚠ [").append(urgency).append("] ");
            sb.append(p.getCompanyName());
            if (p.getJobDescription() != null) {
                sb.append(" — ").append(truncate(p.getJobDescription(), 30));
            }
            sb.append("\n");
            sb.append("  마감: ").append(p.getDeadline()).append("\n");
            sb.append("  ").append(p.getUrl()).append("\n\n");
        }

        return sb.toString();
    }

    public String formatActivePostings(List<JobPosting> postings) {
        if (postings.isEmpty()) return "수집된 활성 공고가 없습니다.";

        StringBuilder sb = new StringBuilder();
        sb.append("수집된 채용공고 목록 (%d건)\n".formatted(postings.size()));
        sb.append("════════════════════\n\n");

        int idx = 1;
        for (JobPosting p : postings) {
            sb.append(idx++).append(". ").append(p.getCompanyName());
            if (p.getJobDescription() != null && !p.getJobDescription().isBlank()) {
                sb.append(" — ").append(truncate(p.getJobDescription(), 35));
            }
            sb.append("\n");
            if (p.getDeadline() != null) {
                sb.append("   마감: ").append(p.getDeadline()).append(daysLeft(p.getDeadline())).append("\n");
            } else {
                sb.append("   마감: 상시채용\n");
            }
            sb.append("   ").append(p.getUrl()).append("\n\n");
        }

        sb.append("채용공고 URL을 보내주시면 자소서를 생성합니다!");
        return sb.toString();
    }

    private String daysLeft(LocalDate deadline) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        if (days < 0) return " (마감됨)";
        if (days == 0) return " (오늘 마감!)";
        if (days == 1) return " (내일 마감!)";
        return " (D-%d)".formatted(days);
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
