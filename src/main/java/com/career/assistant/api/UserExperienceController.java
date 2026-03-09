package com.career.assistant.api;

import com.career.assistant.api.dto.CreateExperienceRequest;
import com.career.assistant.api.dto.ExperienceResponse;
import com.career.assistant.application.ExperienceEmbeddingService;
import com.career.assistant.domain.experience.ExperienceCategory;
import com.career.assistant.domain.experience.UserExperience;
import com.career.assistant.domain.experience.UserExperienceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Slf4j
@Tag(name = "User Experience", description = "사용자 경험/포트폴리오 관리 API")
@RestController
@RequestMapping("/api/experiences")
@RequiredArgsConstructor
public class UserExperienceController {

    private final UserExperienceRepository userExperienceRepository;
    private final ExperienceEmbeddingService experienceEmbeddingService;

    @Operation(summary = "경험 전체 조회")
    @GetMapping
    public ResponseEntity<List<ExperienceResponse>> getAll(
            @RequestParam(required = false) ExperienceCategory category) {
        List<UserExperience> experiences = (category != null)
            ? userExperienceRepository.findByCategory(category)
            : userExperienceRepository.findAll();
        return ResponseEntity.ok(experiences.stream().map(ExperienceResponse::from).toList());
    }

    @Operation(summary = "경험 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ExperienceResponse> getById(@PathVariable Long id) {
        return userExperienceRepository.findById(id)
            .map(exp -> ResponseEntity.ok(ExperienceResponse.from(exp)))
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "경험 등록")
    @PostMapping
    public ResponseEntity<ExperienceResponse> create(@Valid @RequestBody CreateExperienceRequest request) {
        var experience = UserExperience.of(
            request.category(), request.title(), request.description(),
            request.skills(), request.period()
        );
        userExperienceRepository.save(experience);
        try {
            experienceEmbeddingService.indexExperience(experience);
        } catch (Exception e) {
            log.warn("[벡터] 경험 인덱싱 실패 — DB 저장은 완료: {}", e.getMessage());
        }
        return ResponseEntity
            .created(URI.create("/api/experiences/" + experience.getId()))
            .body(ExperienceResponse.from(experience));
    }

    @Operation(summary = "경험 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!userExperienceRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userExperienceRepository.deleteById(id);
        try {
            experienceEmbeddingService.removeExperience(id);
        } catch (Exception e) {
            log.warn("[벡터] 경험 벡터 삭제 실패 — DB 삭제는 완료: {}", e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }
}
