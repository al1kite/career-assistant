package com.career.assistant.infrastructure.embedding;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LocalVectorStore {

    private final Map<Long, float[]> vectors = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Path storePath;

    public LocalVectorStore(ObjectMapper objectMapper,
                            @Value("${vector.store.path:./data/experience-vectors.json}") String storePath) {
        this.objectMapper = objectMapper;
        this.storePath = Path.of(storePath);
    }

    @PostConstruct
    void init() {
        load();
    }

    public void put(long id, float[] vector) {
        float[] prev = vectors.put(id, vector);
        if (!tryPersist()) {
            if (prev != null) {
                vectors.put(id, prev);
            } else {
                vectors.remove(id);
            }
        }
    }

    public void remove(long id) {
        float[] prev = vectors.remove(id);
        if (prev != null && !tryPersist()) {
            vectors.put(id, prev);
        }
    }

    /**
     * 배치 삽입 — tryPersist()를 마지막에 1회만 호출한다.
     * 영속화 실패 시 추가된 항목을 롤백한다.
     */
    public void putAll(Map<Long, float[]> entries) {
        Map<Long, float[]> prevValues = new LinkedHashMap<>();
        entries.forEach((id, vec) -> prevValues.put(id, vectors.put(id, vec)));

        if (!tryPersist()) {
            prevValues.forEach((id, prev) -> {
                if (prev != null) {
                    vectors.put(id, prev);
                } else {
                    vectors.remove(id);
                }
            });
        }
    }

    /**
     * 배치 삭제 — tryPersist()를 마지막에 1회만 호출한다.
     * 영속화 실패 시 삭제된 항목을 복원한다.
     */
    public void removeAll(Set<Long> ids) {
        Map<Long, float[]> removed = new LinkedHashMap<>();
        ids.forEach(id -> {
            float[] prev = vectors.remove(id);
            if (prev != null) removed.put(id, prev);
        });

        if (!removed.isEmpty() && !tryPersist()) {
            vectors.putAll(removed);
        }
    }

    /**
     * 벡터 스토어를 비우고 즉시 영속화한다.
     * 영속화 실패 시 기존 데이터를 복원한다.
     */
    public void clearAndSave() {
        Map<Long, float[]> snapshot = new LinkedHashMap<>(vectors);
        vectors.clear();
        if (!tryPersist()) {
            vectors.putAll(snapshot);
        }
    }

    public boolean isEmpty() {
        return vectors.isEmpty();
    }

    public int size() {
        return vectors.size();
    }

    public Set<Long> ids() {
        return Set.copyOf(vectors.keySet());
    }

    /**
     * 코사인 유사도 기반 top-K 검색.
     * @return ID 리스트 (유사도 내림차순)
     */
    public List<Long> search(float[] queryVector, int topK) {
        return search(queryVector, topK, Set.of());
    }

    /**
     * 코사인 유사도 기반 top-K 검색 (특정 ID 제외).
     * @param excludeIds 검색에서 제외할 ID 집합
     * @return ID 리스트 (유사도 내림차순)
     */
    public List<Long> search(float[] queryVector, int topK, Set<Long> excludeIds) {
        if (vectors.isEmpty()) {
            return List.of();
        }

        return vectors.entrySet().stream()
            .filter(e -> !excludeIds.contains(e.getKey()))
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

    private synchronized boolean tryPersist() {
        try {
            Files.createDirectories(storePath.getParent());
            Map<String, List<Float>> serializable = new LinkedHashMap<>();
            vectors.forEach((id, vec) -> {
                List<Float> floatList = new ArrayList<>(vec.length);
                for (float v : vec) floatList.add(v);
                serializable.put(String.valueOf(id), floatList);
            });
            Path tempFile = storePath.resolveSibling(storePath.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), serializable);
            Files.move(tempFile, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            log.error("[벡터] JSON 저장 실패 — 메모리 변경 롤백: {}", e.getMessage());
            return false;
        }
    }

    private void load() {
        if (!Files.exists(storePath)) {
            log.info("[벡터] 저장 파일 없음 — 빈 스토어로 시작");
            return;
        }
        try {
            Map<String, List<Float>> loaded = objectMapper.readValue(
                storePath.toFile(),
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
