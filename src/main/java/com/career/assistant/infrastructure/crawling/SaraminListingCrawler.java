package com.career.assistant.infrastructure.crawling;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SaraminListingCrawler implements JobSiteCrawler {

    private static final String SEARCH_URL =
        "https://www.saramin.co.kr/zf_user/search/recruit?searchword=%s&recruitPage=%d";
    private static final String BASE_URL = "https://www.saramin.co.kr";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    private static final String[] KEYWORDS = {"백엔드", "Java Spring", "서버개발", "금융 IT", "핀테크"};

    @Override
    public String getSiteName() {
        return "사람인";
    }

    @Override
    public List<JobListingItem> fetchListings(int maxPages) {
        List<JobListingItem> items = new ArrayList<>();

        for (String keyword : KEYWORDS) {
            for (int page = 1; page <= maxPages; page++) {
                try {
                    Document doc = Jsoup.connect(String.format(SEARCH_URL, keyword, page))
                        .userAgent(USER_AGENT)
                        .timeout(10_000)
                        .get();

                    Elements recruitItems = doc.select(".item_recruit");
                    if (recruitItems.isEmpty()) break;

                    for (Element item : recruitItems) {
                        Element companyEl = item.selectFirst(".corp_name a");
                        Element titleEl = item.selectFirst(".job_tit a");
                        Element deadlineEl = item.selectFirst(".job_date .date");

                        if (companyEl == null || titleEl == null) continue;

                        String companyName = companyEl.text().trim();
                        String title = titleEl.text().trim();
                        String deadline = deadlineEl != null ? deadlineEl.text().trim() : null;
                        String href = titleEl.attr("href");
                        String url = href.startsWith("http") ? href : BASE_URL + href;

                        items.add(new JobListingItem(url, companyName, title, deadline, getSiteName()));
                    }

                    log.debug("[사람인] keyword='{}' page {} → {}건", keyword, page, recruitItems.size());
                } catch (Exception e) {
                    log.warn("[사람인] keyword='{}' page {} 크롤링 실패: {}", keyword, page, e.getMessage());
                    break;
                }
            }
        }

        log.info("[사람인] 총 {}건 수집 완료", items.size());
        return items;
    }
}
