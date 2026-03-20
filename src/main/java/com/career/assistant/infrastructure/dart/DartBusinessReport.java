package com.career.assistant.infrastructure.dart;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DartBusinessReport {
    private final String companyOverview;
    private final String businessContent;

    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        if (companyOverview != null && !companyOverview.isBlank()) {
            sb.append("[회사의 개요]\n").append(companyOverview).append("\n\n");
        }
        if (businessContent != null && !businessContent.isBlank()) {
            sb.append("[사업의 내용]\n").append(businessContent).append("\n");
        }
        return sb.toString();
    }
}
