package com.career.assistant.infrastructure.crawling;

public record CrawledJobInfo(
    String companyName,
    String jobDescription,
    String requirements
) {
    public static CrawledJobInfo of(String companyName, String jobDescription, String requirements) {
        return new CrawledJobInfo(companyName, jobDescription, requirements);
    }
}
