package com.career.assistant.infrastructure.dart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class DartClient {

    private static final String BASE_URL = "https://opendart.fss.or.kr/api";
    private static final int SECTION_CHAR_LIMIT = 3000;

    private final DartProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DartClient(DartProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public Optional<DartCompanyInfo> fetchCompanyInfo(String corpCode) {
        try {
            String url = BASE_URL + "/company.json?crtfc_key=%s&corp_code=%s"
                .formatted(properties.getApiKey(), corpCode);

            String body = httpGet(url);
            JsonNode root = objectMapper.readTree(body);

            if (!"000".equals(root.path("status").asText())) {
                log.warn("[DART] 기업개황 API 오류: {}", root.path("message").asText());
                return Optional.empty();
            }

            DartCompanyInfo info = objectMapper.treeToValue(root, DartCompanyInfo.class);
            log.info("[DART] 기업개황 조회 완료: {}", info.getCorpName());
            return Optional.of(info);

        } catch (Exception e) {
            log.warn("[DART] 기업개황 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> fetchLatestBusinessReportNo(String corpCode) {
        try {
            // 사업보고서(A001) 최신 1건 검색
            String url = BASE_URL + "/list.json?crtfc_key=%s&corp_code=%s&pblntf_ty=A&pblntf_detail_ty=A001&page_count=1"
                .formatted(properties.getApiKey(), corpCode);

            String body = httpGet(url);
            JsonNode root = objectMapper.readTree(body);

            if (!"000".equals(root.path("status").asText())) {
                log.debug("[DART] 사업보고서 검색 결과 없음: {}", root.path("message").asText());
                return Optional.empty();
            }

            JsonNode list = root.path("list");
            if (!list.isArray() || list.isEmpty()) return Optional.empty();

            String rceptNo = list.get(0).path("rcept_no").asText();
            String reportName = list.get(0).path("report_nm").asText();
            log.info("[DART] 최신 사업보고서: {} ({})", reportName, rceptNo);
            return Optional.of(rceptNo);

        } catch (Exception e) {
            log.warn("[DART] 사업보고서 번호 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<DartBusinessReport> fetchBusinessReport(String rceptNo) {
        try {
            String url = BASE_URL + "/document.xml?crtfc_key=%s&rcept_no=%s"
                .formatted(properties.getApiKey(), rceptNo);

            byte[] zipBytes = httpGetBytes(url);
            String xmlContent = extractXmlFromZip(zipBytes);
            if (xmlContent == null) {
                log.warn("[DART] 사업보고서 ZIP에서 XML 추출 실패");
                return Optional.empty();
            }

            String companyOverview = extractSection(xmlContent, "회사의 개요");
            String businessContent = extractSection(xmlContent, "사업의 내용");

            if (companyOverview == null && businessContent == null) {
                log.debug("[DART] 사업보고서에서 주요 섹션 추출 실패");
                return Optional.empty();
            }

            log.info("[DART] 사업보고서 파싱 완료 — 개요: {}자, 사업내용: {}자",
                companyOverview != null ? companyOverview.length() : 0,
                businessContent != null ? businessContent.length() : 0);

            return Optional.of(new DartBusinessReport(companyOverview, businessContent));

        } catch (Exception e) {
            log.warn("[DART] 사업보고서 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String extractSection(String xml, String sectionTitle) {
        // HTML 태그가 섞인 DART XML에서 섹션 추출
        // 패턴: <TITLE>sectionTitle</TITLE> 이후의 텍스트
        String upperXml = xml.toUpperCase();
        String upperTitle = sectionTitle.toUpperCase();

        int titleIdx = -1;
        // 여러 형태의 태그 패턴 시도
        for (String pattern : new String[]{
            "<TITLE>" + sectionTitle,
            "<title>" + sectionTitle,
            ">" + sectionTitle + "<",
            sectionTitle
        }) {
            titleIdx = xml.indexOf(pattern);
            if (titleIdx < 0) titleIdx = upperXml.indexOf(pattern.toUpperCase());
            if (titleIdx >= 0) break;
        }

        if (titleIdx < 0) return null;

        // 섹션 시작점 찾기
        int contentStart = xml.indexOf('>', titleIdx) + 1;
        if (contentStart <= 0) return null;

        // 다음 TITLE 태그 또는 PART 태그까지를 섹션 범위로
        int contentEnd = xml.length();
        for (String endPattern : new String[]{"<TITLE", "<title", "<PART", "<part"}) {
            int idx = xml.indexOf(endPattern, contentStart + 100);
            if (idx > 0 && idx < contentEnd) contentEnd = idx;
        }

        String rawSection = xml.substring(contentStart, Math.min(contentEnd, contentStart + SECTION_CHAR_LIMIT * 3));

        // HTML 태그 제거 + 공백 정리
        String cleaned = rawSection
            .replaceAll("<[^>]+>", " ")
            .replaceAll("&nbsp;", " ")
            .replaceAll("&amp;", "&")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("\\s+", " ")
            .trim();

        if (cleaned.length() > SECTION_CHAR_LIMIT) {
            cleaned = cleaned.substring(0, SECTION_CHAR_LIMIT) + "... (이하 생략)";
        }

        return cleaned.isBlank() ? null : cleaned;
    }

    private String extractXmlFromZip(byte[] zipBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".xml") || entry.getName().endsWith(".html")) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.warn("[DART] ZIP 추출 실패: {}", e.getMessage());
        }
        return null;
    }

    private String httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("DART API HTTP " + response.statusCode());
        }
        return response.body();
    }

    private byte[] httpGetBytes(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new RuntimeException("DART API HTTP " + response.statusCode());
        }
        return response.body();
    }
}
