package com.career.assistant.api;

import com.career.assistant.api.dto.JobPostingResponse;
import com.career.assistant.api.dto.JobPostingSearchResponse;
import com.career.assistant.application.CompanyAnalyzer;
import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.domain.jobposting.JobPostingRepository;
import com.career.assistant.infrastructure.crawling.EssayQuestion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Tag(name = "Job Posting", description = "채용공고 조회 API")
@RestController
@RequestMapping("/api/job-postings")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingRepository jobPostingRepository;
    private final CompanyAnalyzer companyAnalyzer;
    private final ObjectMapper objectMapper;

    @Operation(summary = "회사명으로 회사 분석 (채용공고 없이 회사명만으로 분석)")
    @GetMapping("/company-analysis")
    public ResponseEntity<JsonNode> analyzeCompany(@RequestParam String name) {
        String analysisJson = companyAnalyzer.analyzeByName(name);
        if (analysisJson == null) {
            return ResponseEntity.internalServerError().build();
        }
        JsonNode result = parseJson(analysisJson);
        if (result == null) {
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "회사명으로 채용공고 검색 (분석 결과 포함)")
    @GetMapping("/search")
    public ResponseEntity<List<JobPostingSearchResponse>> searchByCompany(
            @RequestParam String company) {
        List<JobPosting> postings = jobPostingRepository.findByCompanyNameContaining(company);
        if (postings.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<JobPostingSearchResponse> results = new ArrayList<>();
        for (JobPosting jp : postings) {
            // 분석이 없으면 자동 실행
            if (jp.getCompanyAnalysis() == null) {
                try {
                    List<EssayQuestion> questions = parseEssayQuestions(jp.getEssayQuestionsJson());
                    String analysis = companyAnalyzer.analyze(jp, questions);
                    if (analysis != null) {
                        jp.updateCompanyAnalysis(analysis);
                        jobPostingRepository.save(jp);
                    }
                } catch (Exception e) {
                    log.warn("[검색] 회사 분석 자동 실행 실패 — {}: {}", jp.getCompanyName(), e.getMessage());
                }
            }

            results.add(toSearchResponse(jp));
        }
        return ResponseEntity.ok(results);
    }

    @Operation(summary = "채용공고 전체 조회")
    @GetMapping
    public ResponseEntity<List<JobPostingResponse>> getAll() {
        var postings = jobPostingRepository.findAll().stream()
            .map(JobPostingResponse::from)
            .toList();
        return ResponseEntity.ok(postings);
    }

    @Operation(summary = "채용공고 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<JobPostingResponse> getById(@PathVariable Long id) {
        return jobPostingRepository.findById(id)
            .map(jp -> ResponseEntity.ok(JobPostingResponse.from(jp)))
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "채용공고 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!jobPostingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        jobPostingRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private JobPostingSearchResponse toSearchResponse(JobPosting jp) {
        List<EssayQuestion> questions = parseEssayQuestions(jp.getEssayQuestionsJson());
        JsonNode analysisNode = parseJson(jp.getCompanyAnalysis());

        return new JobPostingSearchResponse(
            jp.getId(),
            jp.getUrl(),
            jp.getCompanyName(),
            jp.getCompanyType(),
            jp.getJobDescription(),
            jp.getRequirements(),
            questions,
            jp.getDeadline(),
            analysisNode,
            jp.getStatus()
        );
    }

    private List<EssayQuestion> parseEssayQuestions(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[검색] essayQuestions JSON 파싱 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("[검색] companyAnalysis JSON 파싱 실패: {}", e.getMessage());
            return null;
        }
    }
}
