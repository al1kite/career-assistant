package com.career.assistant.api;

import com.career.assistant.api.dto.CreateExperienceRequest;
import com.career.assistant.api.dto.ExperienceResponse;
import com.career.assistant.domain.experience.ExperienceCategory;
import com.career.assistant.domain.experience.UserExperience;
import com.career.assistant.domain.experience.UserExperienceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Tag(name = "User Experience", description = "사용자 경험/포트폴리오 관리 API")
@RestController
@RequestMapping("/api/experiences")
@RequiredArgsConstructor
public class UserExperienceController {

    private final UserExperienceRepository userExperienceRepository;

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
        return ResponseEntity.noContent().build();
    }
}
