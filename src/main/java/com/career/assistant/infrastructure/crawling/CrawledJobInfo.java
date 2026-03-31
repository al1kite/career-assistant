package com.career.assistant.infrastructure.crawling;

import java.util.List;

public record CrawledJobInfo(
    String companyName,
    String jobDescription,
    String requirements,
    String deadline,
    boolean active,
    List<EssayQuestion> essayQuestions,
    List<EmploymentOption> employmentOptions
) {
    public static CrawledJobInfo of(String companyName, String jobDescription, String requirements) {
        return new CrawledJobInfo(companyName, jobDescription, requirements, null, true, List.of(), List.of());
    }

    public static CrawledJobInfo of(String companyName, String jobDescription, String requirements,
                                    String deadline, boolean active) {
        return new CrawledJobInfo(companyName, jobDescription, requirements, deadline, active, List.of(), List.of());
    }

    public static CrawledJobInfo of(String companyName, String jobDescription, String requirements,
                                    String deadline, boolean active, List<EssayQuestion> essayQuestions) {
        return new CrawledJobInfo(companyName, jobDescription, requirements, deadline, active,
                                 essayQuestions != null ? essayQuestions : List.of(), List.of());
    }

    public static CrawledJobInfo of(String companyName, String jobDescription, String requirements,
                                    String deadline, boolean active, List<EssayQuestion> essayQuestions,
                                    List<EmploymentOption> employmentOptions) {
        return new CrawledJobInfo(companyName, jobDescription, requirements, deadline, active,
                                 essayQuestions != null ? essayQuestions : List.of(),
                                 employmentOptions != null ? employmentOptions : List.of());
    }
}
