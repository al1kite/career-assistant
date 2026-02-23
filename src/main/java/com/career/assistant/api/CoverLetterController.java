package com.career.assistant.api;

import com.career.assistant.api.dto.CoverLetterAgentResponse;
import com.career.assistant.api.dto.CoverLetterResponse;
import com.career.assistant.api.dto.CrawlPreviewResponse;
import com.career.assistant.api.dto.GenerateCoverLetterRequest;
import com.career.assistant.application.CoverLetterFacade;
import com.career.assistant.domain.coverletter.CoverLetterRepository;
import com.career.assistant.domain.jobposting.JobPostingRepository;
import com.career.assistant.infrastructure.crawling.CrawledJobInfo;
import com.career.assistant.infrastructure.crawling.CrawlingException;
import com.career.assistant.infrastructure.crawling.JsoupCrawler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "Cover Letter", description = "자소서 생성 API")
@RestController
@RequestMapping("/api/cover-letters")
@RequiredArgsConstructor
public class CoverLetterController {

    private final CoverLetterFacade coverLetterFacade;
    private final CoverLetterRepository coverLetterRepository;
    private final JobPostingRepository jobPostingRepository;
    private final JsoupCrawler jsoupCrawler;

    @Operation(summary = "공고 미리보기 (크롤링만)", description = "URL을 크롤링하여 회사명, 직무설명, 자소서 문항을 미리 확인합니다. 자소서는 생성하지 않습니다.")
    @GetMapping("/preview")
    public ResponseEntity<?> preview(@RequestParam String url) {
        try {
            CrawledJobInfo info = jsoupCrawler.crawl(url);
            return ResponseEntity.ok(CrawlPreviewResponse.from(info));
        } catch (CrawlingException e) {
            log.warn("크롤링 미리보기 실패 - URL: {}, 사유: {}", url, e.getMessage());
            return ResponseEntity.badRequest().body(
                Map.of("error", e.getMessage(), "url", url)
            );
        }
    }

    @Operation(summary = "자소서 생성", description = "채용공고 URL을 입력하면 크롤링 → 회사 분류 → 문항별 자소서 생성을 수행합니다")
    @PostMapping
    public ResponseEntity<?> generate(@Valid @RequestBody GenerateCoverLetterRequest request) {
        try {
            var coverLetters = coverLetterFacade.generateFromUrl(request.url());
            List<CoverLetterResponse> responses = coverLetters.stream()
                .map(CoverLetterResponse::from)
                .toList();
            return ResponseEntity.ok(responses);
        } catch (CrawlingException e) {
            log.warn("크롤링 실패 - URL: {}, 사유: {}", request.url(), e.getMessage());
            return ResponseEntity.badRequest().body(
                Map.of("error", e.getMessage(), "url", request.url())
            );
        }
    }

    @Operation(summary = "자소서 목록 조회", description = "특정 채용공고의 자소서 목록을 조회합니다")
    @GetMapping
    public ResponseEntity<List<CoverLetterResponse>> getByJobPosting(@RequestParam Long jobPostingId) {
        var letters = coverLetterRepository.findByJobPostingId(jobPostingId).stream()
            .map(CoverLetterResponse::from)
            .toList();
        return ResponseEntity.ok(letters);
    }

    @Operation(summary = "자소서 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<CoverLetterResponse> getById(@PathVariable Long id) {
        return coverLetterRepository.findById(id)
            .map(cl -> ResponseEntity.ok(CoverLetterResponse.from(cl)))
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "에이전트 결과 조회", description = "특정 채용공고의 에이전트 검토/개선 반복 이력을 조회합니다")
    @GetMapping("/agent-result")
    public ResponseEntity<List<CoverLetterAgentResponse>> getAgentResult(@RequestParam Long jobPostingId) {
        var allLetters = coverLetterRepository.findByJobPostingId(jobPostingId);
        if (allLetters.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // 문항별로 그룹핑 (questionIndex가 null이면 0으로 취급)
        Map<Integer, List<com.career.assistant.domain.coverletter.CoverLetter>> grouped = allLetters.stream()
            .collect(Collectors.groupingBy(
                cl -> cl.getQuestionIndex() != null ? cl.getQuestionIndex() : 0
            ));

        List<CoverLetterAgentResponse> responses = new ArrayList<>();
        for (var entry : grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            var letters = entry.getValue().stream()
                .sorted(Comparator.comparingInt(com.career.assistant.domain.coverletter.CoverLetter::getVersion))
                .toList();

            var iterations = letters.stream()
                .map(CoverLetterAgentResponse.IterationDetail::from)
                .toList();

            var finalLetter = letters.get(letters.size() - 1);
            var finalResult = CoverLetterAgentResponse.FinalResult.from(finalLetter);

            responses.add(new CoverLetterAgentResponse(
                finalLetter.getQuestionIndex(),
                finalLetter.getQuestionText(),
                iterations,
                finalResult
            ));
        }

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "회사 분석 결과 조회", description = "AI가 생성한 회사 심층 분석 및 문항별 작성 가이드를 조회합니다")
    @GetMapping("/analysis")
    public ResponseEntity<?> getAnalysis(@RequestParam Long jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
            .map(jp -> {
                String analysis = jp.getCompanyAnalysis();
                if (analysis == null || analysis.isBlank()) {
                    return ResponseEntity.ok(Map.of(
                        "jobPostingId", jobPostingId,
                        "companyName", jp.getCompanyName() != null ? jp.getCompanyName() : "",
                        "analysis", Map.of(),
                        "message", "분석 데이터가 아직 생성되지 않았습니다."
                    ));
                }
                try {
                    var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    var analysisNode = objectMapper.readTree(analysis);
                    return ResponseEntity.ok(Map.of(
                        "jobPostingId", jobPostingId,
                        "companyName", jp.getCompanyName() != null ? jp.getCompanyName() : "",
                        "analysis", analysisNode
                    ));
                } catch (Exception e) {
                    return ResponseEntity.ok(Map.of(
                        "jobPostingId", jobPostingId,
                        "companyName", jp.getCompanyName() != null ? jp.getCompanyName() : "",
                        "analysisRaw", analysis
                    ));
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
