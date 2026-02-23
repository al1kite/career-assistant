package com.career.assistant.api;

import com.career.assistant.api.dto.JobPostingResponse;
import com.career.assistant.domain.jobposting.JobPostingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Job Posting", description = "채용공고 조회 API")
@RestController
@RequestMapping("/api/job-postings")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingRepository jobPostingRepository;

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
}
