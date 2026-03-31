package com.career.assistant.api.dto;

import com.career.assistant.domain.jobposting.CompanyType;
import com.career.assistant.domain.jobposting.PipelineStatus;
import com.career.assistant.infrastructure.crawling.EssayQuestion;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;

public record JobPostingSearchResponse(
    Long id,
    String url,
    String companyName,
    CompanyType companyType,
    String jobDescription,
    String requirements,
    List<EssayQuestion> essayQuestions,
    LocalDate deadline,
    JsonNode companyAnalysis,
    PipelineStatus status
) {}
