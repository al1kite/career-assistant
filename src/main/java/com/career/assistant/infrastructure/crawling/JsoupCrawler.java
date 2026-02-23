package com.career.assistant.infrastructure.crawling;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class JsoupCrawler {

    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";

    public CrawledJobInfo crawl(String url) {
        try {
            Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10_000)
                .get();

            return CrawledJobInfo.of(
                extractCompanyName(doc, url),
                extractJobDescription(doc),
                extractRequirements(doc)
            );
        } catch (IOException e) {
            log.error("크롤링 실패: {}", url, e);
            throw new CrawlingException("공고 페이지를 읽어올 수 없습니다: " + url);
        }
    }

    private String extractCompanyName(Document doc, String url) {
        // 원티드
        if (url.contains("wanted.co.kr")) {
            return doc.select("a.company-name").text();
        }
        // 잡코리아
        if (url.contains("jobkorea.co.kr")) {
            return doc.select(".company-name").text();
        }
        // 링커리어
        if (url.contains("linkareer.com")) {
            return doc.select(".organization-name").text();
        }
        return doc.title();
    }

    private String extractJobDescription(Document doc) {
        // 메타 description 먼저 시도
        String meta = doc.select("meta[name=description]").attr("content");
        if (!meta.isBlank()) {
            return meta;
        }
        // body 텍스트에서 주요 내용 추출 (앞 3000자)
        String bodyText = doc.body().text();
        return bodyText.length() > 3000 ? bodyText.substring(0, 3000) : bodyText;
    }

    private String extractRequirements(Document doc) {
        // 자격요건 섹션 파싱 시도
        String requirements = doc.select("[class*=requirement], [class*=qualify]").text();
        return requirements.isBlank() ? "" : requirements;
    }
}
