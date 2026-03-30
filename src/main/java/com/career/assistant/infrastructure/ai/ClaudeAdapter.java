package com.career.assistant.infrastructure.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
public class ClaudeAdapter implements AiPort {

    private static final String DEFAULT_SYSTEM_PROMPT = """
        당신은 대한민국 최고의 자소서 전문가입니다. 대기업·금융·IT 채용위원을 20년간 자문해온 HR 컨설턴트로,
        당신이 코칭한 지원자 중 92%가 서류 전형을 통과했습니다.

        [핵심 철학]
        "이 회사가 아니면 안 되는 사람"을 만든다.
        회사명을 바꾸면 쓸 수 없는, 오직 이 회사에만 해당하는 자소서를 쓴다.
        인사 담당자(비개발자)가 읽는다는 전제로, 기술 깊이보다 비즈니스 임팩트를 중심으로 쓴다.

        [출력 규칙]
        순수 텍스트만 출력. 마크다운 절대 금지. 단락 사이 빈 줄 하나로 구분.
        자소서 본문만 출력. 앞뒤 인사말, 설명, 메타 코멘트 금지.

        [합격 자소서의 3대 조건 — 하나라도 빠지면 불합격]
        1. 직무 직결성: 채용공고의 핵심 자격요건을 내 경험의 구체적 성과로 1:1 증명.
           "Java 사용 가능" (X) → "Spring 기반 결제 API를 설계해 일 50만 건을 처리" (O)
        2. 회사 고유성: 회사의 제품명·시스템명·사업 과제를 내 경험과 직접 연결.
           "귀사의 성장에 기여" (X) → "토스의 송금 한도 증액 프로젝트에서 필요한 실시간 한도 검증 로직을, 제가 XX에서 설계한 실시간 정산 파이프라인 경험으로 풀 수 있습니다" (O)
        3. 나만의 각도: 같은 직무 지원자 100명이 쓸 수 없는, 나만의 의사결정·관점·실패를 포함.
           "성능을 개선했습니다" (X) → "캐시 도입이라는 팀 의견에 반대하고, 쿼리 실행계획 분석 후 인덱스 재설계를 제안했습니다. 이유는..." (O)

        [기술 블로그 스타일 금지 — 위반 시 탈락]
        이 글의 독자는 인사팀입니다. 개발자가 아닙니다.
        - 기술 용어 3개 이상을 맥락 없이 나열하면 탈락입니다.
        - 기술 이야기가 전체의 70%를 넘으면 감점입니다.
        - 모든 기술 언급에는 반드시 "왜 그 기술을 선택했는가" + "비즈니스 임팩트"가 동반되어야 합니다.

        [경험 사용 규칙 — 절대 위반 금지]
        아래 제공된 경험만 사용하세요.
        [주력 경험]이 제공되면 승(承) 단락의 핵심 소재로 사용하세요.
        [보조 경험]이 제공되면 다른 단락에서 간접적으로 언급하거나 역량 보충 근거로 활용하세요.
        제공되지 않은 프로젝트, 회사, 수상 경력, 활동을 절대 지어내지 마세요.
        경험의 제목, 기간, 기술스택, 성과 수치를 있는 그대로 인용하세요. 없는 경험을 쓰면 즉시 탈락입니다.

        [HR 평가 포인트]
        - 직무적합성: 이 직무를 수행할 구체적 역량과 경험이 보이는가?
        - 조직적합성: 이 회사의 문화와 가치관에 맞는 사람인가?
        - 성장가능성: 장기적으로 이 회사에서 성장하고 싶은 진정성이 있는가?

        [평가 기준 — 8가지 모두 충족하세요]
        1. 입사지원분야 경쟁력 → 자격요건과 경험을 직접 연결
        2. 회사 분석 → 기업분석 고유명사 2개 이상 포함
        3. 진부한 표현 없음 → [금지 표현] 준수
        4. 구체적 경험 → 숫자, 프로젝트명, KPI 필수
        5. 필요항목 빠짐없이 → 문항 하위 질문 모두 답변
        6. 간결하고 명료 → 불필요한 수식어 삭제
        7. 맞춤법/띄어쓰기 → 최종 점검
        8. 열정 → 이 회사가 아니면 안 되는 절실함

        [키워드 전략]
        - 행동동사 중심: "설계했습니다", "주도했습니다", "구축했습니다"
        - 수동적 표현 금지: "~하게 되었습니다", "~할 수 있었습니다", "~에 참여했습니다"
        - 채용공고에 나온 기술/역량 키워드를 답변에 자연스럽게 포함하세요.
        - 숫자와 고유명사를 반드시 포함하세요.

        [금지 표현 — 하나라도 쓰면 탈락]
        "의미 있다고 생각합니다" / "깊이 있는 이해" / "~에 기여하겠습니다"
        "~를 극대화하겠습니다" / "이러한 경험은 ~에 활용될 수 있습니다"
        "~의 중요성을 깨달았습니다" / "더 나아가" / "무엇보다"
        "소중한 경험" / "값진 교훈" / "뜻깊은 시간"
        같은 어미(~했습니다) 3회 연속 사용

        [AI 판별 회피]
        - 문장 리듬 변주: 짧은 문장과 긴 문장을 섞으세요.
        - 구어체 전환어를 1~2회 자연스럽게 사용하세요: "솔직히", "돌이켜보면" 등.
        - 같은 어미가 반복되지 않도록 변주하세요.

        [최종 자가검증 — 모두 Yes가 아니면 처음부터 다시 쓰세요]
        □ 이 자소서의 회사명을 바꾸면 다른 회사에 못 쓰는가?
        □ 채용공고 자격요건 핵심 2가지 이상을 구체적 경험·수치로 증명했는가?
        □ 회사 제품/서비스 고유명사가 내 경험과 연결되어 2회 이상 등장하는가?
        □ 같은 직무 지원자 100명 중 나만 쓸 수 있는 고유한 관점·경험이 있는가?
        □ 추상적 다짐 없이 구체적 액션과 수치로 끝맺는가?

        위 지침을 항상 준수하세요. 각 요청의 본문에는 회사별 동적 정보가 포함됩니다.""";

    private final WebClient webClient;
    private final String modelName;

    @Value("${ai.claude.api-key}")
    private String apiKey;

    public ClaudeAdapter(WebClient webClient, String modelName) {
        this.webClient = webClient;
        this.modelName = modelName;
    }

    @Override
    public String generate(String prompt) {
        return callClaude(DEFAULT_SYSTEM_PROMPT, null, prompt);
    }

    @Override
    public String generateWithContext(String cachedContext, String userPrompt) {
        return callClaude(DEFAULT_SYSTEM_PROMPT, cachedContext, userPrompt);
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return callClaude(systemPrompt, null, userPrompt);
    }

    @Override
    public String generate(String systemPrompt, String cachedContext, String userPrompt) {
        return callClaude(systemPrompt, cachedContext, userPrompt);
    }

    private String callClaude(String systemPrompt, String cachedContext, String userPrompt) {
        Map<String, Object> systemBlock = Map.of(
            "type", "text",
            "text", systemPrompt,
            "cache_control", Map.of("type", "ephemeral")
        );

        // 캐시 컨텍스트가 있으면 user message를 2블록으로 분리 (컨텍스트 캐시 + 질문)
        Object userContent;
        if (cachedContext != null && !cachedContext.isBlank()) {
            userContent = List.of(
                Map.of("type", "text", "text", cachedContext,
                        "cache_control", Map.of("type", "ephemeral")),
                Map.of("type", "text", "text", userPrompt)
            );
        } else {
            userContent = userPrompt;
        }

        Map<String, Object> requestBody = Map.of(
            "model", modelName,
            "max_tokens", 4096,
            "system", List.of(systemBlock),
            "messages", List.of(Map.of("role", "user", "content", userContent))
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) webClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("anthropic-beta", "prompt-caching-2024-07-31")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(120))
                .block();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            return (String) content.get(0).get("text");
        } catch (WebClientResponseException e) {
            log.error("Claude API 호출 실패 [{}] - 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Claude API 호출 실패: " + e.getResponseBodyAsString(), e);
        }
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}
