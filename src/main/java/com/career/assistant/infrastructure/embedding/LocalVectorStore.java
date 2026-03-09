package com.career.assistant.infrastructure.embedding;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalVectorStore {

    private static final Path STORE_PATH = Path.of("./data/experience-vectors.json");

    private final Map<Long, float[]> vectors = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @PostConstruct
    void init() {
        load();
    }

    public void put(long id, float[] vector) {
        vectors.put(id, vector);
        save();
    }

    public void remove(long id) {
        vectors.remove(id);
        save();
    }

    public void clear() {
        vectors.clear();
    }

    public boolean isEmpty() {
        return vectors.isEmpty();
    }

    public int size() {
        return vectors.size();
    }

    public boolean contains(long id) {
        return vectors.containsKey(id);
    }

    /**
     * 코사인 유사도 기반 top-K 검색.
     * @return ID 리스트 (유사도 내림차순)
     */
    public List<Long> search(float[] queryVector, int topK) {
        if (vectors.isEmpty()) {
            return List.of();
        }

        return vectors.entrySet().stream()
            .map(e -> Map.entry(e.getKey(), cosineSimilarity(queryVector, e.getValue())))
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(topK)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    private void save() {
        try {
            Files.createDirectories(STORE_PATH.getParent());
            Map<String, List<Float>> serializable = new LinkedHashMap<>();
            vectors.forEach((id, vec) -> {
                List<Float> floatList = new ArrayList<>(vec.length);
                for (float v : vec) floatList.add(v);
                serializable.put(String.valueOf(id), floatList);
            });
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(STORE_PATH.toFile(), serializable);
        } catch (IOException e) {
            log.warn("[벡터] JSON 저장 실패: {}", e.getMessage());
        }
    }

    private void load() {
        if (!Files.exists(STORE_PATH)) {
            log.info("[벡터] 저장 파일 없음 — 빈 스토어로 시작");
            return;
        }
        try {
            Map<String, List<Float>> loaded = objectMapper.readValue(
                STORE_PATH.toFile(),
                new TypeReference<>() {}
            );
            loaded.forEach((idStr, floatList) -> {
                float[] vec = new float[floatList.size()];
                for (int i = 0; i < floatList.size(); i++) vec[i] = floatList.get(i);
                vectors.put(Long.parseLong(idStr), vec);
            });
            log.info("[벡터] 저장 파일에서 {}건 로드 완료", vectors.size());
        } catch (IOException e) {
            log.warn("[벡터] JSON 로드 실패 — 빈 스토어로 시작: {}", e.getMessage());
        }
    }
}
