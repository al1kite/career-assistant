package com.career.assistant.infrastructure.embedding;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OnnxEmbeddingService {

    private static final String MODEL_URL =
        "djl://ai.djl.huggingface.onnxruntime/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2";

    private ZooModel<String, float[]> model;

    @PostConstruct
    void init() {
        try {
            log.info("[임베딩] 모델 로드 시작: paraphrase-multilingual-MiniLM-L12-v2 (첫 실행 시 ~120MB 다운로드)");
            Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelUrls(MODEL_URL)
                .optEngine("OnnxRuntime")
                .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                .optOption("pooling", "mean")
                .build();
            model = criteria.loadModel();
            log.info("[임베딩] 모델 로드 완료 (384차원)");
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            log.error("[임베딩] 모델 로드 실패 — 벡터 검색 비활성화", e);
        }
    }

    @PreDestroy
    void close() {
        if (model != null) {
            model.close();
        }
    }

    public boolean isAvailable() {
        return model != null;
    }

    public float[] embed(String text) {
        if (model == null) {
            throw new IllegalStateException("임베딩 모델이 로드되지 않았습니다.");
        }
        try (Predictor<String, float[]> predictor = model.newPredictor()) {
            return predictor.predict(text);
        } catch (TranslateException e) {
            throw new RuntimeException("텍스트 임베딩 실패: " + text.substring(0, Math.min(50, text.length())), e);
        }
    }
}
