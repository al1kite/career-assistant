package com.career.assistant.application;

import com.career.assistant.domain.jobposting.CompanyType;
import org.springframework.stereotype.Service;

@Service
public class CompanyClassifier {

    public CompanyType classify(String companyName, String jobDescription) {
        String text = (companyName + " " + jobDescription).toLowerCase();

        if (isFinance(text)) return CompanyType.FINANCE;
        if (isLargeCorp(text)) return CompanyType.LARGE_CORP;
        if (isStartup(text)) return CompanyType.STARTUP;
        if (isMidIt(text)) return CompanyType.MID_IT;

        return CompanyType.UNKNOWN;
    }

    private boolean isFinance(String text) {
        return text.contains("은행") || text.contains("금융") || text.contains("증권")
            || text.contains("보험") || text.contains("핀테크") || text.contains("카드");
    }

    private boolean isLargeCorp(String text) {
        return text.contains("삼성") || text.contains("lg") || text.contains("sk")
            || text.contains("현대") || text.contains("카카오") || text.contains("네이버")
            || text.contains("롯데") || text.contains("한화") || text.contains("kt");
    }

    private boolean isStartup(String text) {
        return text.contains("스타트업") || text.contains("시리즈") || text.contains("초기")
            || text.contains("창업") || text.contains("seed") || text.contains("series");
    }

    private boolean isMidIt(String text) {
        return text.contains("개발") || text.contains("플랫폼") || text.contains("솔루션")
            || text.contains("시스템") || text.contains("소프트웨어");
    }
}
