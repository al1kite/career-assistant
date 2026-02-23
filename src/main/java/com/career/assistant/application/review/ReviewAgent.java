package com.career.assistant.application.review;

import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.infrastructure.ai.AiPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ReviewAgent {

    private static final String REVIEWER_SYSTEM_PROMPT = """
        당신은 대기업 15년차 채용팀장입니다. 지금까지 자소서 5만 건 이상을 검토했습니다.
        당신의 유일한 기준: "이 지원자를 면접에 부를 것인가?"

        [채점 기준 — 절대 평가]
        - 80점 이상: 면접에 부르고 싶은 수준. 이 정도는 되어야 합격 후보.
        - 90점 이상: 채용팀 전체에 회람하고 싶은 수준. 극히 드묾.
        - 70점대: 아쉽지만 서류 탈락. 한두 가지만 고치면 될 수준.
        - 60점대 이하: 기본기 부족. 전면 재작성 필요.

        [평가 항목 — 8개]
        1. 답변적합도 (가중치 10%): 질문이 묻는 것에 정확히 답했는가? 질문 의도를 벗어나면 0점.
        2. 직무적합도 (가중치 20%): 당장 투입하면 일할 수 있겠는가? 기술 스택, 유사 경험, 구체적 성과.
        3. 조직적합도 (가중치 15%): 회사명을 바꾸면 못 쓸 정도로 특화됐는가? 이 회사만의 문화/방향 이해.
        4. 구체성 (가중치 20%): 숫자, 프로젝트명, 정량적 성과가 있는가? "열심히 했다"는 0점.
        5. 진정성/개성 (가중치 10%): 이 사람만의 고유한 이야기인가? 누구나 쓸 수 있는 말이면 0점.
        6. AI탐지 위험도 (가중치 10%): AI가 쓴 것 같은 패턴이 있는가? (높을수록 위험) 같은 어미 반복, 추상적 미사여구, 정형화된 구조.
        7. 논리적 구조 (가중치 5%): 기승전결 흐름이 명확한가? 각 단락이 유기적으로 연결되는가?
        8. 키워드 활용 (가중치 10%): 채용공고의 핵심 키워드가 자연스럽게 녹아있는가?

        [피드백 규칙]
        - violations: 반드시 문장을 인용하고 구체적 문제를 지적하세요. "좀 더 구체적으로" 같은 모호한 피드백 금지.
        - improvements: 현재 문장을 인용 → 문제점 → 개선 방향 → 개선 예시를 포함하세요.

        [출력 형식]
        반드시 아래 JSON 형식만 출력하세요. 다른 텍스트, 설명, 마크다운 절대 금지.
        {
          "scores": {
            "answerRelevance": 0~100,
            "jobFit": 0~100,
            "orgFit": 0~100,
            "specificity": 0~100,
            "authenticity": 0~100,
            "aiDetectionRisk": 0~100,
            "logicalStructure": 0~100,
            "keywordUsage": 0~100
          },
          "violations": ["문장 인용 + 구체적 문제 지적", ...],
          "improvements": ["현재 문장 인용 → 개선 방향 → 예시", ...],
          "overallComment": "전체 총평 (2~3문장)"
        }""";

    private final AiPort claudeSonnet;
    private final AiPort claudeHaiku;
    private final ObjectMapper objectMapper;

    public ReviewAgent(@Qualifier("claudeSonnet") AiPort claudeSonnet,
                       @Qualifier("claudeHaiku") AiPort claudeHaiku,
                       ObjectMapper objectMapper) {
        this.claudeSonnet = claudeSonnet;
        this.claudeHaiku = claudeHaiku;
        this.objectMapper = objectMapper;
    }

    public ReviewResult review(String draft, JobPosting jobPosting, String question, int iterationNum) {
        String userPrompt = buildReviewPrompt(draft, jobPosting, question, iterationNum);
        AiPort reviewer = (iterationNum == 1) ? claudeSonnet : claudeHaiku;

        try {
            log.info("[에이전트] {}차 검토 — 모델: {}", iterationNum, reviewer.getModelName());
            String response = reviewer.generate(REVIEWER_SYSTEM_PROMPT, userPrompt);
            return parseReviewResponse(response);
        } catch (Exception e) {
            log.error("[에이전트] 검토 중 오류 발생 (iteration {}): {}", iterationNum, e.getMessage());
            return ReviewResult.fallback();
        }
    }

    private String buildReviewPrompt(String draft, JobPosting jobPosting, String question, int iterationNum) {
        return """
            [검토 대상 자소서 — %d차 검토]

            [채용공고 정보]
            회사: %s
            직무설명: %s
            자격요건: %s

            [자소서 문항]
            %s

            [자소서 내용]
            %s

            위 자소서를 8개 평가 항목으로 채점하고, violations과 improvements를 구체적으로 작성하세요.
            반드시 순수 JSON만 출력하세요.""".formatted(
                iterationNum,
                jobPosting.getCompanyName(),
                jobPosting.getJobDescription() != null ? jobPosting.getJobDescription() : "",
                jobPosting.getRequirements() != null ? jobPosting.getRequirements() : "",
                question != null ? question : "(단일 자소서)",
                draft
            );
    }

    private ReviewResult parseReviewResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            JsonNode scoresNode = root.get("scores");
            ReviewResult.Scores scores = new ReviewResult.Scores(
                scoresNode.get("answerRelevance").asInt(),
                scoresNode.get("jobFit").asInt(),
                scoresNode.get("orgFit").asInt(),
                scoresNode.get("specificity").asInt(),
                scoresNode.get("authenticity").asInt(),
                scoresNode.get("aiDetectionRisk").asInt(),
                scoresNode.get("logicalStructure").asInt(),
                scoresNode.get("keywordUsage").asInt()
            );

            List<String> violations = parseStringArray(root.get("violations"));
            List<String> improvements = parseStringArray(root.get("improvements"));
            String overallComment = root.has("overallComment") ? root.get("overallComment").asText() : "";

            int totalScore = ReviewResult.calculateTotalScore(scores);
            String grade = ReviewResult.resolveGrade(totalScore);

            return new ReviewResult(scores, totalScore, grade, violations, improvements, overallComment, json);
        } catch (Exception e) {
            log.warn("[에이전트] 검토 결과 JSON 파싱 실패, 폴백 적용: {}", e.getMessage());
            return ReviewResult.fallback();
        }
    }

    private String extractJson(String response) {
        String cleaned = response.trim();

        // 마크다운 코드 펜스 제거
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
        }

        // { ~ } 추출
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }

        return cleaned;
    }

    private List<String> parseStringArray(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }
}
