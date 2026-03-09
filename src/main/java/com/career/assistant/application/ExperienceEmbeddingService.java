package com.career.assistant.application;

import com.career.assistant.domain.experience.UserExperience;
import com.career.assistant.domain.experience.UserExperienceRepository;
import com.career.assistant.infrastructure.embedding.LocalVectorStore;
import com.career.assistant.infrastructure.embedding.OnnxEmbeddingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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
        reindexAll();
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
            .filter(java.util.Objects::nonNull)
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

    public void reindexAll() {
        List<UserExperience> all = userExperienceRepository.findAll();
        if (all.isEmpty()) {
            log.info("[벡터] DB 경험 0건 — 인덱싱 스킵");
            return;
        }

        vectorStore.clear();
        for (UserExperience exp : all) {
            String text = buildEmbeddingText(exp);
            float[] vector = embeddingService.embed(text);
            vectorStore.put(exp.getId(), vector);
        }
        log.info("[벡터] 전체 경험 {}건 인덱싱 완료", all.size());
    }

    private String buildEmbeddingText(UserExperience exp) {
        StringBuilder sb = new StringBuilder();
        sb.append(exp.getTitle());
        if (exp.getDescription() != null) {
            sb.append(" ").append(exp.getDescription());
        }
        if (exp.getSkills() != null && !exp.getSkills().isBlank()) {
            sb.append(" ").append(exp.getSkills());
        }
        return sb.toString();
    }
}
