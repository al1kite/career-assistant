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
        try {
            incrementalSync();
        } catch (Exception e) {
            log.error("[벡터] 시작 시 동기화 실패 — 벡터 검색 없이 진행: {}", e.getMessage());
        }
    }

    public List<UserExperience> retrieveRelevant(String query) {
        return retrieveRelevant(query, DEFAULT_TOP_K);
    }

    public List<UserExperience> retrieveRelevant(String query, int topK) {
        if (!embeddingService.isAvailable() || vectorStore.isEmpty()) {
            log.debug("[벡터] 비활성 또는 빈 스토어 — findAll 폴백");
            return userExperienceRepository.findAll();
        }

        try {
            float[] queryVector = embeddingService.embed(query);
            List<Long> ids = vectorStore.search(queryVector, topK);

            if (ids.isEmpty()) {
                return userExperienceRepository.findAll();
            }

            List<UserExperience> results = userExperienceRepository.findAllById(ids);

            var idToExp = results.stream()
                .collect(Collectors.toMap(UserExperience::getId, e -> e));
            List<UserExperience> ordered = ids.stream()
                .map(idToExp::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            log.info("[벡터] 쿼리에서 {}건 검색 (전체 {}건 중)", ordered.size(), vectorStore.size());
            return ordered;
        } catch (Exception e) {
            log.warn("[벡터] 검색 실패 — findAll 폴백: {}", e.getMessage());
            return userExperienceRepository.findAll();
        }
    }

    public void indexExperience(UserExperience exp) {
        if (!embeddingService.isAvailable()) return;
        if (exp.getId() == null) {
            log.warn("[벡터] 경험 ID가 null — 인덱싱 스킵");
            return;
        }
        try {
            String text = buildEmbeddingText(exp);
            float[] vector = embeddingService.embed(text);
            vectorStore.put(exp.getId(), vector);
            log.info("[벡터] 경험 인덱싱: id={}", exp.getId());
        } catch (Exception e) {
            log.warn("[벡터] 경험 인덱싱 실패 (id={}): {}", exp.getId(), e.getMessage());
        }
    }

    public void removeExperience(Long id) {
        vectorStore.remove(id);
        log.info("[벡터] 경험 삭제: id={}", id);
    }

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

        // 벡터에만 있고 DB에 없는 것 → 배치 삭제
        Set<Long> toRemove = storeIds.stream()
            .filter(id -> !dbIds.contains(id))
            .collect(Collectors.toSet());
        if (!toRemove.isEmpty()) {
            vectorStore.removeAll(toRemove);
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
            try {
                String text = buildEmbeddingText(exp);
                float[] vector = embeddingService.embed(text);
                batch.put(exp.getId(), vector);
            } catch (Exception e) {
                log.warn("[벡터] 경험 임베딩 실패 (id={}) — 스킵: {}", exp.getId(), e.getMessage());
            }
        }
        if (!batch.isEmpty()) {
            vectorStore.putAll(batch);
        }
        log.info("[벡터] 증분 동기화 완료 — 신규 {}건 추가 (전체 {}건)", batch.size(), vectorStore.size());
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
            try {
                String text = buildEmbeddingText(exp);
                float[] vector = embeddingService.embed(text);
                batch.put(exp.getId(), vector);
            } catch (Exception e) {
                log.warn("[벡터] 경험 임베딩 실패 (id={}) — 스킵: {}", exp.getId(), e.getMessage());
            }
        }
        vectorStore.clearAndSave();
        if (!batch.isEmpty()) {
            vectorStore.putAll(batch);
        }
        log.info("[벡터] 전체 경험 {}건 인덱싱 완료 (실패 {}건 스킵)", batch.size(), all.size() - batch.size());
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
