package com.career.assistant.domain.jobposting;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_postings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String url;

    @Column(name = "company_name")
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_type")
    private CompanyType companyType;

    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @Enumerated(EnumType.STRING)
    private PipelineStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static JobPosting from(String url) {
        JobPosting posting = new JobPosting();
        posting.url = url;
        posting.status = PipelineStatus.FETCHED;
        posting.createdAt = LocalDateTime.now();
        return posting;
    }

    public void updateCrawledInfo(String companyName, String jobDescription, String requirements) {
        this.companyName = companyName;
        this.jobDescription = jobDescription;
        this.requirements = requirements;
        this.status = PipelineStatus.CLEANED;
    }

    public void classify(CompanyType companyType) {
        this.companyType = companyType;
        this.status = PipelineStatus.SCORED;
    }

    public void markDrafted() {
        this.status = PipelineStatus.DRAFTED;
    }

    public void markFailed() {
        this.status = PipelineStatus.FAILED;
    }
}
