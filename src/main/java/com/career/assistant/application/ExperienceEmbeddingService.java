package com.career.assistant.application;

import com.career.assistant.domain.experience.UserExperience;
import com.career.assistant.domain.experience.UserExperienceRepository;
import com.career.assistant.infrastructure.embedding.LocalVectorStore;
import com.career.assistant.infrastructure.embedding.OnnxEmbeddingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceEmbeddingService {

    private static final int DEFAULT_TOP_K = 5;

    private final OnnxEmbeddingService embeddingService;
    private final LocalVectorStore vectorStore;
    private final UserExperienceRepository userExperienceRepository;

    @PostConstruct
    void syncOnStartup() {
        if (!embeddingService.isAvailable()) {
            log.warn("[벡터] 임베딩 모델 비활성 — 벡터 검색 대신 전체 경험 사용");
            return;
        }
        incrementalSync();
    }

    public List<UserExperience> retrieveRelevant(String query) {
        return retrieveRelevant(query, DEFAULT_TOP_K);
    }

    public List<UserExperience> retrieveRelevant(String query, int topK) {
        if (!embeddingService.isAvailable() || vectorStore.isEmpty()) {
            log.debug("[벡터] 비활성 또는 빈 스토어 — findAll 폴백");
            return userExperienceRepository.findAll();
        }

        float[] queryVector = embeddingService.embed(query);
        List<Long> ids = vectorStore.search(queryVector, topK);

        if (ids.isEmpty()) {
            return userExperienceRepository.findAll();
        }

        List<UserExperience> results = userExperienceRepository.findAllById(ids);

        // 검색 순서(유사도 내림차순) 유지
        var idToExp = results.stream()
            .collect(Collectors.toMap(UserExperience::getId, e -> e));
        List<UserExperience> ordered = ids.stream()
            .map(idToExp::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        log.info("[벡터] 쿼리에서 {}건 검색 (전체 {}건 중)", ordered.size(), vectorStore.size());
        return ordered;
    }

    public void indexExperience(UserExperience exp) {
        if (!embeddingService.isAvailable()) return;
        String text = buildEmbeddingText(exp);
        float[] vector = embeddingService.embed(text);
        vectorStore.put(exp.getId(), vector);
        log.info("[벡터] 경험 인덱싱: id={}, title={}", exp.getId(), exp.getTitle());
    }

    public void removeExperience(Long id) {
        vectorStore.remove(id);
        log.info("[벡터] 경험 삭제: id={}", id);
    }

    /**
     * DB ID와 벡터 스토어 ID를 비교하여 변경분만 인덱싱한다.
     * 추가된 경험만 임베딩하고, 삭제된 경험은 벡터에서 제거한다.
     */
    private void incrementalSync() {
        List<UserExperience> allExperiences = userExperienceRepository.findAll();
        if (allExperiences.isEmpty()) {
            if (!vectorStore.isEmpty()) {
                vectorStore.clearAndSave();
            }
            log.info("[벡터] DB 경험 0건 — 인덱싱 스킵");
            return;
        }

        Set<Long> dbIds = allExperiences.stream()
            .map(UserExperience::getId)
            .collect(Collectors.toSet());
        Set<Long> storeIds = vectorStore.ids();

        // 벡터에만 있고 DB에 없는 것 → 삭제
        for (Long storeId : storeIds) {
            if (!dbIds.contains(storeId)) {
                vectorStore.remove(storeId);
            }
        }

        // DB에만 있고 벡터에 없는 것 → 추가
        List<UserExperience> toIndex = allExperiences.stream()
            .filter(exp -> !storeIds.contains(exp.getId()))
            .toList();

        if (toIndex.isEmpty()) {
            log.info("[벡터] 변경 없음 — 기존 {}건 유지", vectorStore.size());
            return;
        }

        Map<Long, float[]> batch = new LinkedHashMap<>();
        for (UserExperience exp : toIndex) {
            String text = buildEmbeddingText(exp);
            float[] vector = embeddingService.embed(text);
            batch.put(exp.getId(), vector);
        }
        vectorStore.putAll(batch);
        log.info("[벡터] 증분 동기화 완료 — 신규 {}건 추가 (전체 {}건)", toIndex.size(), vectorStore.size());
    }

    public void reindexAll() {
        List<UserExperience> all = userExperienceRepository.findAll();
        if (all.isEmpty()) {
            vectorStore.clearAndSave();
            log.info("[벡터] DB 경험 0건 — 인덱싱 스킵");
            return;
        }

        Map<Long, float[]> batch = new LinkedHashMap<>();
        for (UserExperience exp : all) {
            String text = buildEmbeddingText(exp);
            float[] vector = embeddingService.embed(text);
            batch.put(exp.getId(), vector);
        }
        vectorStore.clearAndSave();
        vectorStore.putAll(batch);
        log.info("[벡터] 전체 경험 {}건 인덱싱 완료", all.size());
    }

    private String buildEmbeddingText(UserExperience exp) {
        StringBuilder sb = new StringBuilder();
        if (exp.getTitle() != null && !exp.getTitle().isBlank()) {
            sb.append(exp.getTitle());
        }
        if (exp.getDescription() != null && !exp.getDescription().isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(exp.getDescription());
        }
        if (exp.getSkills() != null && !exp.getSkills().isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(exp.getSkills());
        }
        return sb.toString();
    }
}
