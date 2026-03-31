package com.career.assistant.infrastructure.ai;

import com.career.assistant.domain.jobposting.CompanyType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiRouter {

    @Qualifier("claudeSonnet")
    private final AiPort claudeSonnet;

    @Qualifier("claudeHaiku")
    private final AiPort claudeHaiku;

    public AiPort route(CompanyType companyType) {
        // 자소서 생성은 항상 Sonnet — 품질이 최우선
        return claudeSonnet;
    }
}
