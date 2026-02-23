package com.career.assistant.api.dto;

import com.career.assistant.domain.jobposting.CompanyType;
import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.domain.jobposting.PipelineStatus;

import java.time.LocalDateTime;

public record JobPostingResponse(
    Long id,
    String url,
    String companyName,
    CompanyType companyType,
    String jobDescription,
    String requirements,
    String companyAnalysis,
    PipelineStatus status,
    LocalDateTime createdAt
) {
    public static JobPostingResponse from(JobPosting jp) {
        return new JobPostingResponse(
            jp.getId(),
            jp.getUrl(),
            jp.getCompanyName(),
            jp.getCompanyType(),
            jp.getJobDescription(),
            jp.getRequirements(),
            jp.getCompanyAnalysis(),
            jp.getStatus(),
            jp.getCreatedAt()
        );
    }
}
