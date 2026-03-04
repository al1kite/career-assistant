package com.career.assistant.infrastructure.crawling;

import java.util.List;

public interface JobSiteCrawler {
    String getSiteName();
    List<JobListingItem> fetchListings(int maxPages);
}
