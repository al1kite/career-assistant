package com.career.assistant.application;

import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.infrastructure.ai.AiPort;
import com.career.assistant.infrastructure.crawling.EssayQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CompanyAnalyzer {

    private static final String SYSTEM_PROMPT = """
        당신은 대한민국 기업 리서치 전문가입니다.
        회사명과 채용공고 정보를 바탕으로 심층 분석을 제공합니다.
        반드시 JSON 형식으로만 응답하세요. 마크다운 코드블록(```) 없이 순수 JSON만 출력하세요.

        [핵심 원칙]
        - 구체적 제품명, 서비스명, 시스템명 등 고유명사를 반드시 포함하세요.
        - "금융 IT 서비스", "솔루션 기업" 같은 포괄적 표현 금지. "exture+(초저지연 주문 처리 시스템)" 수준의 구체성 필요.
        - 각 필드를 충분히 상세하게 작성하세요. 피상적 분석은 가치가 없습니다.""";

    private final AiPort claudeSonnet;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompanyAnalyzer(@Qualifier("claudeSonnet") AiPort claudeSonnet) {
        this.claudeSonnet = claudeSonnet;
    }

    /**
     * 회사 정보와 자소서 문항을 분석하여 구조화된 JSON을 생성합니다.
     * 분석 실패 시 null을 반환합니다 (graceful degradation).
     */
    public String analyze(JobPosting jobPosting, List<EssayQuestion> questions) {
        try {
            String userPrompt = buildAnalysisPrompt(jobPosting, questions);
            String response = claudeSonnet.generate(SYSTEM_PROMPT, userPrompt);

            String jsonStr = extractJson(response);
            if (jsonStr != null) {
                // ObjectMapper로 실제 파싱하여 유효성 검증
                objectMapper.readTree(jsonStr);
                log.info("[분석] 회사 분석 완료 - {} ({}자)", jobPosting.getCompanyName(), jsonStr.length());
                return jsonStr;
            }

            log.warn("[분석] AI 응답에서 유효한 JSON을 추출할 수 없습니다. 응답 길이: {}", response.length());
            log.debug("[분석] AI 응답 구조 — 길이: {}, '{' 포함: {}", response.length(), response.contains("{"));
            return null;
        } catch (Exception e) {
            log.error("[분석] 회사 분석 실패 - {}: {}", jobPosting.getCompanyName(), e.getMessage());
            return null;
        }
    }

    /**
     * AI 응답에서 JSON 객체를 추출합니다.
     * 마크다운 코드블록, 앞뒤 텍스트를 제거하고 가장 바깥 {...}을 추출합니다.
     */
    private String extractJson(String response) {
        if (response == null || response.isBlank()) return null;

        String trimmed = response.strip();

        // 마크다운 코드블록 제거
        if (trimmed.startsWith("```")) {
            int endIdx = trimmed.lastIndexOf("```");
            if (endIdx > 3) {
                trimmed = trimmed.substring(trimmed.indexOf('\n') + 1, endIdx).strip();
            }
        }

        // 가장 바깥 { ... } 블록 추출
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }

        return null;
    }

    private String buildAnalysisPrompt(JobPosting jobPosting, List<EssayQuestion> questions) {
        String questionsText = "";
        if (questions != null && !questions.isEmpty()) {
            questionsText = questions.stream()
                .map(q -> "문항 %d: %s (글자수 제한: %d자)".formatted(q.number(), q.questionText(), q.charLimit()))
                .collect(Collectors.joining("\n"));
        }

        return """
            아래 채용공고 정보를 분석하여 JSON으로 응답하세요.

            [회사 정보]
            회사명: %s
            회사 유형: %s
            직무 설명: %s
            자격 요건: %s

            [자소서 문항]
            %s

            다음 JSON 구조로 정확히 응답하세요 (값은 한국어로):
            {
              "companyOverview": "회사 소개. 핵심 사업, 시장 내 위치, 매출 규모 등을 구체적 고유명사와 함께 작성",
              "coreProducts": [
                {"name": "제품/서비스 고유명사", "description": "이 제품이 무엇이고 왜 중요한지 2~3문장"},
                {"name": "제품/서비스 고유명사 2", "description": "설명"}
              ],
              "competitiveAdvantage": "경쟁사 대비 이 회사만의 차별점. 구체적 기술/사업 우위를 고유명사로",
              "competitors": [
                {"name": "경쟁사명", "differentiation": "이 회사가 경쟁사 대비 뛰어난 점"},
                {"name": "경쟁사명 2", "differentiation": "차별점"}
              ],
              "hiringReason": "이 포지션을 채용하는 이유 추론 (사업 확장, 신규 프로젝트, 기술 전환 등)",
              "idealCandidate": "이 포지션의 이상적 지원자 프로필",
              "companyValues": "회사의 핵심 가치와 조직 문화",
              "techDirection": "현재 기술 방향성과 투자/전환 동향. 구체적 기술명과 이유를 포함",
              "businessChallenges": "이 회사가 현재 직면한 사업/기술 과제 2~3가지",
              "recentNews": "최근 1~2년간 주요 뉴스, 발표, 인수합병, 신사업 등",
              "recentTrends": "이 회사/업계의 최근 동향과 전략 방향",
              "questionGuides": [
                {
                  "questionIndex": 1,
                  "questionText": "문항 원문",
                  "questionType": "지원동기/핵심역량/문제해결/협업리더십/입사후포부/성장과정/일반 중 하나",
                  "writingStrategy": "이 문항에서 어떤 경험을 어떤 구조로 풀어야 하는지 구체적 전략. 3~5문장으로 상세히.",
                  "mustInclude": ["반드시 포함할 키워드나 포인트 (회사 고유명사 1개 이상 필수)"],
                  "avoid": ["피해야 할 표현이나 접근"],
                  "exampleOpening": "추천 도입 문장 예시 (이 회사 제품/서비스 고유명사를 포함한 구체적 첫 문장)"
                }
              ]
            }

            주의사항:
            - 모든 필드에서 "금융 IT", "솔루션 기업" 같은 포괄 표현 금지. 반드시 제품명, 시스템명, 서비스명 등 고유명사를 포함하세요.
            - coreProducts는 2~4개, competitors는 2~3개 작성하세요.
            - questionGuides는 위에 제시된 자소서 문항 수만큼 생성하세요. 문항이 없으면 빈 배열 []로 두세요.
            - mustInclude에 반드시 회사 제품/서비스 고유명사 1개 이상을 포함하세요.
            - writingStrategy는 3~5문장으로 상세히 작성하세요.
            """.formatted(
                jobPosting.getCompanyName() != null ? jobPosting.getCompanyName() : "미상",
                jobPosting.getCompanyType() != null ? jobPosting.getCompanyType().name() : "UNKNOWN",
                jobPosting.getJobDescription() != null ? jobPosting.getJobDescription() : "(정보 없음)",
                jobPosting.getRequirements() != null ? jobPosting.getRequirements() : "(정보 없음)",
                questionsText.isBlank() ? "(자소서 문항 없음)" : questionsText
            );
    }
}
