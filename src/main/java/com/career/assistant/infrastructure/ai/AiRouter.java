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
        return switch (companyType) {
            case LARGE_CORP, FINANCE -> claudeSonnet; // 정교한 문체 필요
            case STARTUP, MID_IT, UNKNOWN -> claudeHaiku; // 빠르고 저렴하게
        };
    }
}
