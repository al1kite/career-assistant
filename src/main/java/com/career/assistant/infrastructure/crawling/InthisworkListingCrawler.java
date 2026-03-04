package com.career.assistant.infrastructure.crawling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 인디스워크(inthiswork.com) 채용공고 리스팅 크롤러.
 * WordPress REST API를 사용하여 최신 채용공고를 수집.
 * 제목 형식: "회사명｜포지션" / 마감일: excerpt에서 추출
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InthisworkListingCrawler implements JobSiteCrawler {

    private static final String API_URL = "https://inthiswork.com/wp-json/wp/v2/posts?per_page=20&page=%d&orderby=date&order=desc";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final Pattern DEADLINE_PATTERN = Pattern.compile(
        "접수기한\\s*(\\d{4}[./-]\\d{1,2}[./-]\\d{1,2})"
    );
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{4}[./-]\\d{1,2}[./-]\\d{1,2})"
    );

    private final ObjectMapper objectMapper;

    @Override
    public String getSiteName() {
        return "인디스워크";
    }

    @Override
    public List<JobListingItem> fetchListings(int maxPages) {
        List<JobListingItem> items = new ArrayList<>();

        for (int page = 1; page <= maxPages; page++) {
            try {
                String json = Jsoup.connect(String.format(API_URL, page))
                    .ignoreContentType(true)
                    .userAgent(USER_AGENT)
                    .timeout(10_000)
                    .execute()
                    .body();

                JsonNode posts = objectMapper.readTree(json);
                if (!posts.isArray() || posts.isEmpty()) break;

                for (JsonNode post : posts) {
                    String title = stripHtml(post.path("title").path("rendered").asText(""));
                    String link = post.path("link").asText("");
                    String excerpt = stripHtml(post.path("excerpt").path("rendered").asText(""));

                    if (title.isBlank() || link.isBlank()) continue;

                    String companyName = extractCompanyName(title);
                    String positionTitle = extractPositionTitle(title);
                    String deadline = extractDeadline(excerpt);

                    items.add(new JobListingItem(link, companyName, positionTitle, deadline, getSiteName()));
                }

                log.debug("[인디스워크] page {} → {}건 수집", page, posts.size());
            } catch (Exception e) {
                log.warn("[인디스워크] page {} 크롤링 실패: {}", page, e.getMessage());
                break;
            }
        }

        log.info("[인디스워크] 총 {}건 수집 완료", items.size());
        return items;
    }

    private String extractCompanyName(String title) {
        // "회사명｜포지션" 또는 "회사명|포지션" 패턴
        if (title.contains("｜")) return title.split("｜")[0].trim();
        if (title.contains("|")) return title.split("\\|")[0].trim();
        if (title.contains("–")) return title.split("–")[0].trim();
        if (title.contains("-")) return title.split("-")[0].trim();
        return title;
    }

    private String extractPositionTitle(String title) {
        if (title.contains("｜")) {
            String[] parts = title.split("｜", 2);
            return parts.length > 1 ? parts[1].trim() : title;
        }
        if (title.contains("|")) {
            String[] parts = title.split("\\|", 2);
            return parts.length > 1 ? parts[1].trim() : title;
        }
        return title;
    }

    private String extractDeadline(String excerpt) {
        if (excerpt == null || excerpt.isBlank()) return null;

        // "접수기한 2026.03.15" 패턴 우선
        Matcher m = DEADLINE_PATTERN.matcher(excerpt);
        if (m.find()) return m.group(1);

        // 일반 날짜 패턴 폴백
        Matcher dm = DATE_PATTERN.matcher(excerpt);
        if (dm.find()) return dm.group(1);

        return null;
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("&[^;]+;", "").trim();
    }
}
