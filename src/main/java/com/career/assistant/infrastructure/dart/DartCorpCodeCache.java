package com.career.assistant.infrastructure.dart;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class DartCorpCodeCache {

    private static final String CORP_CODE_URL = "https://opendart.fss.or.kr/api/corpCode.xml";
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final DartProperties properties;
    private volatile Map<String, String> corpCodeMap = Map.of();
    private Instant lastLoadTime;
    private volatile boolean loading = false;

    public DartCorpCodeCache(DartProperties properties) {
        this.properties = properties;
    }

    public Optional<String> findCorpCode(String companyName) {
        if (companyName == null || companyName.isBlank()) return Optional.empty();
        if (!isApiKeyConfigured()) return Optional.empty();

        ensureLoaded();
        // 아직 로딩 중이면 빈 결과 반환 (다운로드 완료 후 다음 호출에서 사용)
        if (corpCodeMap.isEmpty()) return Optional.empty();

        String normalized = companyName.trim();

        // 1. 정확 매칭
        String code = corpCodeMap.get(normalized);
        if (code != null) return Optional.of(code);

        // 2. "(주)" 제거 매칭
        String withoutCorp = normalized.replace("(주)", "").replace("㈜", "").trim();
        code = corpCodeMap.get(withoutCorp);
        if (code != null) return Optional.of(code);

        // 3. "(주)" 붙여서 매칭
        code = corpCodeMap.get("(주)" + withoutCorp);
        if (code != null) return Optional.of(code);
        code = corpCodeMap.get("㈜" + withoutCorp);
        if (code != null) return Optional.of(code);

        // 4. 부분 매칭 (결과가 정확히 1건일 때만) — 불변 스냅샷이므로 스레드 안전
        Map<String, String> snapshot = corpCodeMap;
        int partialMatchCount = 0;
        Map.Entry<String, String> singleMatch = null;
        for (Map.Entry<String, String> e : snapshot.entrySet()) {
            String key = e.getKey();
            if (key.contains(withoutCorp) || withoutCorp.contains(key)) {
                partialMatchCount++;
                if (partialMatchCount == 1) {
                    singleMatch = e;
                } else {
                    break;
                }
            }
        }
        if (partialMatchCount == 1 && singleMatch != null) {
            log.info("[DART] 부분 매칭 성공: '{}' → '{}'", companyName, singleMatch.getKey());
            return Optional.of(singleMatch.getValue());
        }

        log.debug("[DART] 회사 코드 매핑 실패: {} (부분매칭 {}건)", companyName, partialMatchCount);
        return Optional.empty();
    }

    private boolean isApiKeyConfigured() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    private void ensureLoaded() {
        if (!corpCodeMap.isEmpty() && lastLoadTime != null
            && Duration.between(lastLoadTime, Instant.now()).compareTo(CACHE_TTL) < 0) {
            return;
        }

        // 이미 다른 스레드가 로딩 중이면 대기하지 않고 바로 반환
        if (loading) return;

        synchronized (this) {
            // double-check
            if (!corpCodeMap.isEmpty() && lastLoadTime != null
                && Duration.between(lastLoadTime, Instant.now()).compareTo(CACHE_TTL) < 0) {
                return;
            }
            if (loading) return;
            loading = true;
        }

        try {
            Path xmlPath = Path.of(properties.getCorpCodePath());

            // 로컬 캐시 파일이 있고 7일 이내면 재사용
            if (Files.exists(xmlPath)) {
                var lastMod = Files.getLastModifiedTime(xmlPath).toInstant();
                if (Duration.between(lastMod, Instant.now()).compareTo(CACHE_TTL) < 0) {
                    loadFromXmlFile(xmlPath);
                    return;
                }
            }

            // DART API에서 ZIP 다운로드
            downloadAndExtract(xmlPath);
            loadFromXmlFile(xmlPath);

        } catch (Exception e) {
            log.warn("[DART] corpCode 로딩 실패: {}", e.getMessage());
        } finally {
            loading = false;
        }
    }

    private void downloadAndExtract(Path targetXmlPath) throws Exception {
        String url = CORP_CODE_URL + "?crtfc_key=" + properties.getApiKey();
        log.info("[DART] corpCode.xml 다운로드 중...");

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("DART API 응답 오류: " + response.statusCode());
        }

        // ZIP 압축 해제 → XML 저장
        Path parent = targetXmlPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".xml")) {
                    Files.copy(zis, targetXmlPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    log.info("[DART] corpCode.xml 저장 완료: {}", targetXmlPath);
                    break;
                }
            }
        }
    }

    private void loadFromXmlFile(Path xmlPath) throws Exception {
        log.info("[DART] corpCode.xml 파싱 중: {}", xmlPath);

        Map<String, String> tempMap = new LinkedHashMap<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlPath.toFile());

        NodeList list = doc.getElementsByTagName("list");
        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);
            String corpCode = getTagValue(el, "corp_code");
            String corpName = getTagValue(el, "corp_name");
            if (corpCode != null && corpName != null) {
                tempMap.put(corpName.trim(), corpCode.trim());
            }
        }

        // 불변 스냅샷으로 한 번에 교체 — 읽기 스레드와의 동시성 문제 방지
        corpCodeMap = Map.copyOf(tempMap);
        lastLoadTime = Instant.now();
        log.info("[DART] corpCode 로딩 완료: {}개 기업", corpCodeMap.size());
    }

    private String getTagValue(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0 && nodes.item(0).getTextContent() != null) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }
}
