package com.career.assistant.application;

import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.infrastructure.ai.AiPort;
import com.career.assistant.infrastructure.crawling.EssayQuestion;
import com.career.assistant.infrastructure.dart.DartClient;
import com.career.assistant.infrastructure.dart.DartCompanyData;
import com.career.assistant.infrastructure.dart.DartCompanyInfo;
import com.career.assistant.infrastructure.dart.DartCorpCodeCache;
import com.career.assistant.infrastructure.dart.DartBusinessReport;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CompanyAnalyzer {

    private static final String SYSTEM_PROMPT = """
        당신은 대한민국 기업 리서치 전문가입니다.
        회사명과 채용공고 정보를 바탕으로 심층 분석을 제공합니다.
        반드시 JSON 형식으로만 응답하세요. 마크다운 코드블록(```) 없이 순수 JSON만 출력하세요.
        JSON 문자열 값 안에 줄바꿈을 넣지 마세요. 한 줄로 작성하세요.

        [핵심 원칙]
        - 구체적 제품명, 서비스명, 시스템명 등 고유명사를 반드시 포함하세요.
        - "금융 IT 서비스", "솔루션 기업" 같은 포괄적 표현 금지. "exture+(초저지연 주문 처리 시스템)" 수준의 구체성 필요.
        - 각 필드를 충분히 상세하게 작성하세요. 피상적 분석은 가치가 없습니다.""";

    private final AiPort claudeHaiku;
    private final DartClient dartClient;
    private final DartCorpCodeCache dartCorpCodeCache;

    private static final ObjectMapper LENIENT_MAPPER = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
        .enable(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)
        .build();

    public CompanyAnalyzer(@Qualifier("claudeHaiku") AiPort claudeHaiku,
                           DartClient dartClient,
                           DartCorpCodeCache dartCorpCodeCache) {
        this.claudeHaiku = claudeHaiku;
        this.dartClient = dartClient;
        this.dartCorpCodeCache = dartCorpCodeCache;
    }

    public String analyze(JobPosting jobPosting, List<EssayQuestion> questions) {
        String companyName = jobPosting.getCompanyName();
        DartCompanyData dartData = fetchDartData(companyName);

        // 1차: DART 포함 시도
        try {
            String prompt = buildAnalysisPrompt(jobPosting, questions, dartData);
            return callAiAndParse(prompt, companyName, dartData.hasData());
        } catch (Exception e) {
            log.warn("[분석] 1차 시도 실패 (DART: {}) — DART 없이 재시도: {}",
                dartData.hasData(), e.getMessage());
        }

        // 2차: DART 없이 재시도
        try {
            String prompt = buildAnalysisPrompt(jobPosting, questions, new DartCompanyData(null, null));
            return callAiAndParse(prompt, companyName, false);
        } catch (Exception e) {
            log.error("[분석] 2차 시도도 실패: {}", e.getMessage());
            return null;
        }
    }

    public String analyzeByName(String companyName) {
        DartCompanyData dartData = fetchDartData(companyName);

        // 1차: DART 포함 시도
        try {
            String prompt = buildCompanyOnlyPrompt(companyName, dartData);
            return callAiAndParse(prompt, companyName, dartData.hasData());
        } catch (Exception e) {
            log.warn("[분석] 회사명 단독 분석 1차 실패 (DART: {}) — 재시도: {}",
                dartData.hasData(), e.getMessage());
        }

        // 2차: DART 없이 재시도
        try {
            String prompt = buildCompanyOnlyPrompt(companyName, new DartCompanyData(null, null));
            return callAiAndParse(prompt, companyName, false);
        } catch (Exception e) {
            log.error("[분석] 회사명 단독 분석 2차도 실패: {}", e.getMessage());
            return null;
        }
    }

    private String callAiAndParse(String userPrompt, String companyName, boolean hasDart) {
        log.info("[분석] AI 분석 요청 - {} (프롬프트 {}자, DART: {})",
            companyName, userPrompt.length(), hasDart ? "활용" : "없음");

        String response = claudeHaiku.generate(SYSTEM_PROMPT, userPrompt);
        if (response == null || response.isBlank()) {
            log.error("[분석] AI 응답이 비어있습니다.");
            return null;
        }

        // { ... } 추출
        String rawJson = extractJson(response);
        if (rawJson == null) {
            log.error("[분석] AI 응답에서 {{...}} 구조를 찾을 수 없습니다. 응답 앞 500자: {}",
                response.substring(0, Math.min(500, response.length())));
            return null;
        }

        // 1차: 관용 파서로 파싱 → 표준 JSON 재직렬화
        try {
            var tree = LENIENT_MAPPER.readTree(rawJson);
            String normalized = LENIENT_MAPPER.writeValueAsString(tree);
            log.info("[분석] 회사 분석 완료 - {} ({}자, DART: {})",
                companyName, normalized.length(), hasDart ? "활용" : "없음");
            return normalized;
        } catch (Exception e) {
            log.warn("[분석] 1차 JSON 파싱 실패: {}", e.getMessage());
        }

        // 2차: 문자열 내부 제어문자 이스케이프 후 재시도
        try {
            String cleaned = escapeControlCharsInStrings(rawJson);
            var tree = LENIENT_MAPPER.readTree(cleaned);
            String normalized = LENIENT_MAPPER.writeValueAsString(tree);
            log.info("[분석] 회사 분석 완료 (정리 후) - {} ({}자)", companyName, normalized.length());
            return normalized;
        } catch (Exception e) {
            log.warn("[분석] 2차 JSON 파싱 실패: {}", e.getMessage());
        }

        // 3차: 파싱 불가해도 원본 반환 — null보다 낫다
        log.warn("[분석] JSON 파싱 실패했지만 원본 반환 ({}자). 다운스트림에서 fallback 처리됨.", rawJson.length());
        return rawJson;
    }

    private DartCompanyData fetchDartData(String companyName) {
        try {
            Optional<String> corpCode = dartCorpCodeCache.findCorpCode(companyName);
            if (corpCode.isEmpty()) {
                log.debug("[DART] 회사 코드 매핑 실패 (비상장 또는 미등록): {}", companyName);
                return new DartCompanyData(null, null);
            }

            DartCompanyInfo companyInfo = dartClient.fetchCompanyInfo(corpCode.get()).orElse(null);

            DartBusinessReport report = null;
            Optional<String> rceptNo = dartClient.fetchLatestBusinessReportNo(corpCode.get());
            if (rceptNo.isPresent()) {
                report = dartClient.fetchBusinessReport(rceptNo.get()).orElse(null);
            }

            DartCompanyData data = new DartCompanyData(companyInfo, report);
            if (data.hasData()) {
                log.info("[DART] 기업 데이터 조회 완료: {} (개황: {}, 사업보고서: {})",
                    companyName, companyInfo != null ? "O" : "X", report != null ? "O" : "X");
            }
            return data;

        } catch (Exception e) {
            log.warn("[DART] 데이터 조회 실패 — 기존 방식으로 진행: {}", e.getMessage());
            return new DartCompanyData(null, null);
        }
    }

    private String extractJson(String response) {
        if (response == null || response.isBlank()) return null;

        String trimmed = response.strip();

        if (trimmed.startsWith("```")) {
            int endIdx = trimmed.lastIndexOf("```");
            if (endIdx > 3) {
                trimmed = trimmed.substring(trimmed.indexOf('\n') + 1, endIdx).strip();
            }
        }

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }

        return null;
    }

    /**
     * JSON 문자열 값 내부의 제어문자(줄바꿈, 탭 등)를 이스케이프.
     * trailing comma도 제거.
     */
    private String escapeControlCharsInStrings(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                sb.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }

            if (inString) {
                switch (c) {
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> { if (c >= 0x20) sb.append(c); }
                }
            } else {
                sb.append(c);
            }
        }

        String result = sb.toString();
        result = result.replaceAll(",\\s*}", "}");
        result = result.replaceAll(",\\s*]", "]");
        return result;
    }

    private String buildCompanyOnlyPrompt(String companyName, DartCompanyData dartData) {
        String dartSection = "";
        if (dartData != null && dartData.hasData()) {
            dartSection = "\n" + dartData.toPromptText() + "\n";
        }

        return """
            아래 회사를 심층 분석하여 JSON으로 응답하세요.

            [회사 정보]
            회사명: %s
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
              "companyValues": "회사의 핵심 가치와 조직 문화",
              "techDirection": "현재 기술 방향성과 투자/전환 동향. 구체적 기술명과 이유를 포함",
              "businessChallenges": "이 회사가 현재 직면한 사업/기술 과제 2~3가지",
              "recentNews": "최근 1~2년간 주요 뉴스, 발표, 인수합병, 신사업 등",
              "recentTrends": "이 회사/업계의 최근 동향과 전략 방향"
            }

            주의사항:
            - 반드시 "%s"만 분석하세요. 모회사·자회사·같은 그룹의 다른 계열사를 혼동하지 마세요.
            - 확실하지 않은 정보는 "정확한 정보 확인 불가"로 표기하세요. 잘못된 정보보다 낫습니다.
            - 모든 필드에서 "금융 IT", "솔루션 기업" 같은 포괄 표현 금지. 반드시 제품명, 시스템명, 서비스명 등 고유명사를 포함하세요.
            - coreProducts는 2~4개, competitors는 2~3개 작성하세요.
            - DART 공시 데이터가 제공되었다면 이를 적극 활용하여 정확한 정보를 작성하세요.
            """.formatted(companyName, dartSection, companyName);
    }

    private String buildAnalysisPrompt(JobPosting jobPosting, List<EssayQuestion> questions,
                                        DartCompanyData dartData) {
        String questionsText = "";
        if (questions != null && !questions.isEmpty()) {
            questionsText = questions.stream()
                .map(q -> "문항 %d: %s (글자수 제한: %d자)".formatted(q.number(), q.questionText(), q.charLimit()))
                .collect(Collectors.joining("\n"));
        }

        String dartSection = "";
        if (dartData != null && dartData.hasData()) {
            dartSection = "\n" + dartData.toPromptText() + "\n";
        }

        return """
            아래 채용공고 정보를 분석하여 JSON으로 응답하세요.

            [회사 정보]
            회사명: %s
            회사 유형: %s
            직무 설명: %s
            자격 요건: %s
            %s
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
                  "questionType": "포트폴리오/지원동기/핵심역량/문제해결/협업리더십/입사후포부/성장과정/장단점/일반 중 하나",
                  "writingStrategy": "이 문항에서 어떤 경험을 어떤 구조로 풀어야 하는지 구체적 전략. 3~5문장으로 상세히.",
                  "mustInclude": ["반드시 포함할 키워드나 포인트 (회사 고유명사 1개 이상 필수)"],
                  "avoid": ["피해야 할 표현이나 접근"],
                  "exampleOpening": "추천 도입 문장 예시 (이 회사 제품/서비스 고유명사를 포함한 구체적 첫 문장)"
                }
              ]
            }

            주의사항:
            - 반드시 "%s"만 분석하세요. 모회사·자회사·같은 그룹의 다른 계열사를 혼동하지 마세요. 예: "현대무빅스" 분석 시 "현대엘리베이터", "현대자동차" 정보를 쓰면 즉시 실패입니다.
            - 확실하지 않은 정보는 "정확한 정보 확인 불가"로 표기하세요. 잘못된 정보보다 낫습니다.
            - 모든 필드에서 "금융 IT", "솔루션 기업" 같은 포괄 표현 금지. 반드시 제품명, 시스템명, 서비스명 등 고유명사를 포함하세요.
            - coreProducts는 2~4개, competitors는 2~3개 작성하세요.
            - questionGuides는 위에 제시된 자소서 문항 수만큼 생성하세요. 문항이 없으면 빈 배열 []로 두세요.
            - mustInclude에 반드시 회사 제품/서비스 고유명사 1개 이상을 포함하세요.
            - writingStrategy는 3~5문장으로 상세히 작성하세요.
            - DART 공시 데이터가 제공되었다면 이를 적극 활용하여 정확한 정보를 작성하세요.
            """.formatted(
                jobPosting.getCompanyName() != null ? jobPosting.getCompanyName() : "미상",
                jobPosting.getCompanyType() != null ? jobPosting.getCompanyType().name() : "UNKNOWN",
                jobPosting.getJobDescription() != null ? jobPosting.getJobDescription() : "(정보 없음)",
                jobPosting.getRequirements() != null ? jobPosting.getRequirements() : "(정보 없음)",
                dartSection,
                questionsText.isBlank() ? "(자소서 문항 없음)" : questionsText,
                jobPosting.getCompanyName() != null ? jobPosting.getCompanyName() : "미상"
            );
    }
}
