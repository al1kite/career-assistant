package com.career.assistant.application.jobcollector;

import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.domain.jobposting.JobPostingRepository;
import com.career.assistant.infrastructure.crawling.JobListingItem;
import com.career.assistant.infrastructure.crawling.JobSiteCrawler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Slf4j
@Service
public class JobCollectorService {

    private final List<JobSiteCrawler> crawlers;
    private final JobPostingRepository jobPostingRepository;
    private final JobCollectorMessageFormatter messageFormatter;
    private final List<String> keywords;
    private final int maxPages;

    public JobCollectorService(
        List<JobSiteCrawler> crawlers,
        JobPostingRepository jobPostingRepository,
        JobCollectorMessageFormatter messageFormatter,
        @Value("${job-collector.keywords:백엔드,Java,Spring,서버,금융,증권,핀테크}") String keywordsCsv,
        @Value("${job-collector.max-pages:3}") int maxPages
    ) {
        this.crawlers = crawlers;
        this.jobPostingRepository = jobPostingRepository;
        this.messageFormatter = messageFormatter;
        this.keywords = Arrays.stream(keywordsCsv.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .toList();
        this.maxPages = maxPages;
    }

    @Transactional
    public List<JobPosting> collectNewPostings() {
        List<JobPosting> newPostings = new ArrayList<>();

        // 기존 DB 공고의 회사명+제목 조합을 미리 로드 (크로스 플랫폼 중복 감지용)
        Set<String> existingKeys = new HashSet<>();
        for (JobPosting existing : jobPostingRepository.findAll()) {
            existingKeys.add(normalizeForDedup(existing.getCompanyName(), existing.getJobDescription()));
        }

        // 이번 수집에서 새로 추가된 것도 중복 체크에 포함
        Set<String> sessionKeys = new HashSet<>(existingKeys);

        for (JobSiteCrawler crawler : crawlers) {
            int siteNew = 0;
            try {
                log.info("[수집] {} 크롤링 시작", crawler.getSiteName());
                List<JobListingItem> listings = crawler.fetchListings(maxPages);

                for (JobListingItem item : listings) {
                    if (!matchesKeywords(item)) continue;
                    if (jobPostingRepository.existsByUrl(item.url())) continue;

                    // 크로스 플랫폼 중복: 회사명+제목이 유사한 공고가 이미 존재하면 건너뜀
                    String dedupKey = normalizeForDedup(item.companyName(), item.title());
                    if (sessionKeys.contains(dedupKey)) {
                        log.debug("[수집] 크로스 플랫폼 중복 건너뜀: {} — {} ({})",
                            item.companyName(), item.title(), item.siteName());
                        continue;
                    }

                    LocalDate deadline = parseDeadline(item.deadline());
                    JobPosting posting = JobPosting.fromCollected(
                        item.url(), item.companyName(), item.title(), deadline
                    );
                    jobPostingRepository.save(posting);
                    newPostings.add(posting);
                    sessionKeys.add(dedupKey);
                    siteNew++;
                }

                log.info("[수집] {} 완료 — 신규 {}건", crawler.getSiteName(), siteNew);
            } catch (Exception e) {
                log.error("[수집] {} 크롤링 실패", crawler.getSiteName(), e);
            }
        }

        log.info("[수집] 전체 신규 공고 {}건 저장 완료", newPostings.size());
        return newPostings;
    }

    /**
     * 크로스 플랫폼 중복 감지를 위한 정규화 키 생성.
     * 회사명+제목에서 공백/특수문자를 제거하고 소문자로 변환.
     * ex) "삼성전자 | 백엔드 개발자 채용" → "삼성전자백엔드개발자채용"
     */
    private String normalizeForDedup(String companyName, String title) {
        String raw = (companyName != null ? companyName : "") + (title != null ? title : "");
        return raw.toLowerCase()
            .replace("(주)", "")                                // (주) 리터럴 제거
            .replace("주식회사", "")                              // 주식회사 제거
            .replaceAll("\\(.*?\\)", "")                        // 괄호 내용 제거
            .replaceAll("[\\s｜|\\-–—·•()（）\\[\\]{}]", "")     // 공백, 구분자, 괄호 제거
            .replaceAll("[.,;:!?]", "");                        // 구두점 제거
    }

    public List<JobPosting> findUpcomingDeadlines() {
        LocalDate today = LocalDate.now();
        LocalDate threeDaysLater = today.plusDays(3);
        return jobPostingRepository.findByDeadlineBetween(today, threeDaysLater);
    }

    public String getActivePostingsSummary() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<JobPosting> active = jobPostingRepository
            .findByDeadlineAfterOrDeadlineIsNullOrderByDeadlineAsc(yesterday);
        return messageFormatter.formatActivePostings(active);
    }

    private boolean matchesKeywords(JobListingItem item) {
        String text = (item.companyName() + " " + item.title()).toLowerCase();
        return keywords.stream().anyMatch(text::contains);
    }

    private static final DateTimeFormatter[] FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
    };

    private LocalDate parseDeadline(String deadline) {
        if (deadline == null || deadline.isBlank()) return null;

        // "~ 2026.03.15" 같은 패턴에서 날짜 부분만 추출
        String cleaned = deadline.replaceAll("[^0-9.\\-/]", "").trim();
        if (cleaned.isBlank()) return null;

        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                return LocalDate.parse(cleaned, fmt);
            } catch (Exception ignored) {
            }
        }

        // ISO datetime (2026-03-15T23:59:59) 처리
        try {
            if (cleaned.contains("T") || deadline.contains("T")) {
                String dateOnly = deadline.contains("T") ? deadline.split("T")[0] : cleaned.split("T")[0];
                return LocalDate.parse(dateOnly.replaceAll("[^0-9\\-]", ""));
            }
        } catch (Exception ignored) {
        }

        log.debug("[수집] 마감일 파싱 실패: {}", deadline);
        return null;
    }
}
