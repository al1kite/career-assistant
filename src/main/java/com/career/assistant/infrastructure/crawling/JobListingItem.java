package com.career.assistant.infrastructure.crawling;

public record JobListingItem(
    String url,
    String companyName,
    String title,
    String deadline,
    String siteName
) {}
