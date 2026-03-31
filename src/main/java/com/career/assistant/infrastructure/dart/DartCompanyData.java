package com.career.assistant.infrastructure.dart;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DartCompanyData {
    private final DartCompanyInfo companyInfo;
    private final DartBusinessReport businessReport;

    public boolean hasData() {
        return companyInfo != null || businessReport != null;
    }

    public String toPromptText() {
        if (!hasData()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("[DART 공시 데이터 — 실제 공시 기반이므로 AI 사전지식보다 우선하세요]\n\n");

        if (companyInfo != null) {
            sb.append("[기업개황]\n").append(companyInfo.toSummary()).append("\n");
        }
        if (businessReport != null) {
            String reportSummary = businessReport.toSummary();
            if (!reportSummary.isBlank()) {
                sb.append(reportSummary);
            }
        }

        return sb.toString();
    }
}
