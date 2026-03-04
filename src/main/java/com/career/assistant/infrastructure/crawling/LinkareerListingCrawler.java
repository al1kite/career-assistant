package com.career.assistant.infrastructure.crawling;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 링커리어(linkareer.com) 채용공고 리스팅 크롤러.
 * 링커리어는 SPA(Next.js)로 메인 리스트가 클라이언트 렌더링되지만,
 * 개별 activity 페이지의 og: 메타태그에서 제목/회사 정보를 추출할 수 있음.
 * 최신 activity ID 범위를 순회하며 채용 관련 공고를 수집.
 */
@Slf4j
@Component
public class LinkareerListingCrawler implements JobSiteCrawler {

    private static final String ACTIVITY_URL = "https://linkareer.com/activity/%d";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final int ITEMS_PER_PAGE = 20;
    private static final String[] RECRUIT_KEYWORDS = {"채용", "신입", "인턴", "경력", "모집", "recruit"};

    @Override
    public String getSiteName() {
        return "링커리어";
    }

    @Override
    public List<JobListingItem> fetchListings(int maxPages) {
        List<JobListingItem> items = new ArrayList<>();
        int maxItems = maxPages * ITEMS_PER_PAGE;

        // 최신 activity ID를 추정하여 역순 탐색
        long latestId = findLatestActivityId();
        if (latestId <= 0) {
            log.warn("[링커리어] 최신 activity ID를 찾을 수 없습니다");
            return items;
        }

        int checked = 0;
        int notFound = 0;

        for (long id = latestId; id > 0 && items.size() < maxItems; id--) {
            if (notFound >= 10) break; // 연속 10개 404이면 중단
            checked++;
            if (checked > maxItems * 3) break; // 너무 많이 탐색하지 않기

            try {
                Document doc = Jsoup.connect(String.format(ACTIVITY_URL, id))
                    .userAgent(USER_AGENT)
                    .timeout(5_000)
                    .followRedirects(true)
                    .get();

                String ogTitle = getMetaContent(doc, "og:title");
                if (ogTitle == null || ogTitle.isBlank()) {
                    notFound++;
                    continue;
                }
                notFound = 0;

                // 채용 관련 공고만 필터링
                if (!isRecruitPosting(ogTitle, doc)) continue;

                String companyName = extractCompanyName(ogTitle);
                String title = extractTitle(ogTitle);
                String deadline = extractDeadline(doc);
                String url = String.format(ACTIVITY_URL, id);

                items.add(new JobListingItem(url, companyName, title, deadline, getSiteName()));
            } catch (org.jsoup.HttpStatusException e) {
                if (e.getStatusCode() == 404) {
                    notFound++;
                } else {
                    log.debug("[링커리어] activity {} 접근 실패: {}", id, e.getStatusCode());
                }
            } catch (Exception e) {
                log.debug("[링커리어] activity {} 크롤링 실패: {}", id, e.getMessage());
            }
        }

        log.info("[링커리어] 총 {}건 수집 완료 ({}건 탐색)", items.size(), checked);
        return items;
    }

    private long findLatestActivityId() {
        // sitemap에서 확인된 최근 범위(305000~306000)를 기반으로 최신 ID 탐색
        for (long id = 310000; id >= 300000; id -= 100) {
            try {
                int status = Jsoup.connect(String.format(ACTIVITY_URL, id))
                    .userAgent(USER_AGENT)
                    .timeout(3_000)
                    .followRedirects(true)
                    .execute()
                    .statusCode();
                if (status == 200) {
                    // 더 정밀하게 최신 ID 탐색
                    for (long fineId = id + 99; fineId >= id; fineId--) {
                        try {
                            int fineStatus = Jsoup.connect(String.format(ACTIVITY_URL, fineId))
                                .userAgent(USER_AGENT)
                                .timeout(3_000)
                                .execute()
                                .statusCode();
                            if (fineStatus == 200) return fineId;
                        } catch (Exception ignored) {
                        }
                    }
                    return id;
                }
            } catch (Exception ignored) {
            }
        }
        return 306000; // 폴백
    }

    private boolean isRecruitPosting(String ogTitle, Document doc) {
        String text = ogTitle.toLowerCase();
        String description = getMetaContent(doc, "og:description");
        if (description != null) text += " " + description.toLowerCase();

        for (String keyword : RECRUIT_KEYWORDS) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private String extractCompanyName(String ogTitle) {
        // "회사명 | 포지션 - 링커리어" 또는 "회사명 채용" 패턴
        if (ogTitle.contains("|")) {
            return ogTitle.split("\\|")[0].trim();
        }
        if (ogTitle.contains("｜")) {
            return ogTitle.split("｜")[0].trim();
        }
        if (ogTitle.contains(" - ")) {
            return ogTitle.split(" - ")[0].trim();
        }
        return ogTitle.length() > 30 ? ogTitle.substring(0, 30) : ogTitle;
    }

    private String extractTitle(String ogTitle) {
        // 링커리어 suffix 제거
        String cleaned = ogTitle.replaceAll("\\s*[-|]\\s*링커리어.*$", "").trim();
        if (cleaned.contains("|")) {
            String[] parts = cleaned.split("\\|", 2);
            return parts.length > 1 ? parts[1].trim() : cleaned;
        }
        if (cleaned.contains("｜")) {
            String[] parts = cleaned.split("｜", 2);
            return parts.length > 1 ? parts[1].trim() : cleaned;
        }
        return cleaned;
    }

    private String extractDeadline(Document doc) {
        // og:description이나 메타태그에서 마감일 추출
        String desc = getMetaContent(doc, "og:description");
        if (desc != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{4}[./-]\\d{1,2}[./-]\\d{1,2})")
                .matcher(desc);
            if (m.find()) return m.group(1);
        }

        // 본문에서 마감, 접수기간 관련 텍스트 탐색
        Elements elements = doc.select("*:containsOwn(마감), *:containsOwn(접수기간), *:containsOwn(모집기간)");
        for (Element el : elements) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{4}[./-]\\d{1,2}[./-]\\d{1,2})")
                .matcher(el.text());
            if (m.find()) return m.group(1);
        }

        return null;
    }

    private String getMetaContent(Document doc, String property) {
        Element meta = doc.selectFirst("meta[property=" + property + "]");
        if (meta != null) return meta.attr("content");
        meta = doc.selectFirst("meta[name=" + property + "]");
        return meta != null ? meta.attr("content") : null;
    }
}
