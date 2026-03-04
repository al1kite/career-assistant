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
public class JasoseolListingCrawler implements JobSiteCrawler {

    private static final String API_URL = "https://jasoseol.com/api/v1/employment_companies?page=%d";
    private static final String DETAIL_URL = "https://jasoseol.com/recruit/%d";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";

    private final ObjectMapper objectMapper;

    @Override
    public String getSiteName() {
        return "자소설닷컴";
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

                JsonNode root = objectMapper.readTree(json);
                JsonNode data = root.isArray() ? root : root.path("data");
                if (!data.isArray() || data.isEmpty()) break;

                for (JsonNode node : data) {
                    String name = node.path("name").asText(node.path("company_name").asText(""));
                    String title = node.path("title").asText("");
                    String endTime = node.path("end_time").asText(null);
                    long id = node.path("id").asLong(0);
                    if (id == 0 || name.isBlank()) continue;

                    items.add(new JobListingItem(
                        String.format(DETAIL_URL, id),
                        name,
                        title,
                        endTime,
                        getSiteName()
                    ));
                }

                log.debug("[자소설닷컴] page {} → {}건 수집", page, data.size());
            } catch (Exception e) {
                log.warn("[자소설닷컴] page {} 크롤링 실패: {}", page, e.getMessage());
                break;
            }
        }

        log.info("[자소설닷컴] 총 {}건 수집 완료", items.size());
        return items;
    }
}
