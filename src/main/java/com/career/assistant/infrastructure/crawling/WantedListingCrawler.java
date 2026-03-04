package com.career.assistant.infrastructure.crawling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WantedListingCrawler implements JobSiteCrawler {

    private static final String API_URL =
        "https://www.wanted.co.kr/api/v4/jobs?country=kr&tag_type_ids=518&locations=all&years=-1&limit=20&offset=%d";
    private static final String DETAIL_URL = "https://www.wanted.co.kr/wd/%d";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";

    private final ObjectMapper objectMapper;

    @Override
    public String getSiteName() {
        return "원티드";
    }

    @Override
    public List<JobListingItem> fetchListings(int maxPages) {
        List<JobListingItem> items = new ArrayList<>();

        for (int page = 0; page < maxPages; page++) {
            try {
                String json = Jsoup.connect(String.format(API_URL, page * 20))
                    .ignoreContentType(true)
                    .userAgent(USER_AGENT)
                    .timeout(10_000)
                    .execute()
                    .body();

                JsonNode root = objectMapper.readTree(json);
                JsonNode data = root.path("data");
                if (!data.isArray() || data.isEmpty()) break;

                for (JsonNode node : data) {
                    long id = node.path("id").asLong(0);
                    String position = node.path("position").asText("");
                    String companyName = node.path("company").path("name").asText("");
                    String dueTime = node.path("due_time").asText(null);
                    if (id == 0 || companyName.isBlank()) continue;

                    items.add(new JobListingItem(
                        String.format(DETAIL_URL, id),
                        companyName,
                        position,
                        dueTime,
                        getSiteName()
                    ));
                }

                log.debug("[원티드] page {} → {}건 수집", page, data.size());
            } catch (Exception e) {
                log.warn("[원티드] page {} 크롤링 실패: {}", page, e.getMessage());
                break;
            }
        }

        log.info("[원티드] 총 {}건 수집 완료", items.size());
        return items;
    }
}
