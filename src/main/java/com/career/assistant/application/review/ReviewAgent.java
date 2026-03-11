package com.career.assistant.application.review;

import com.career.assistant.domain.experience.UserExperience;
import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.infrastructure.ai.AiPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReviewAgent {

    private static final String REVIEWER_SYSTEM_PROMPT = """
        당신은 인사팀 소속 15년차 채용팀장입니다. 기술팀이 아닙니다.
        지금까지 자소서 5만 건 이상을 검토했습니다.
        당신의 유일한 기준: "이 지원자를 면접에 부를 것인가?"
        비개발자가 읽어도 지원자의 역량이 느껴지는지를 기준으로 평가하세요.

        [채점 기준 — 절대 평가, 엄격하게]
        당신은 후한 점수를 절대 주지 않습니다. 빈말 칭찬 없이 냉정하게 평가하세요.
        - 90점 이상: 현직자도 감탄할 수준. 100건 중 1건 나올까. 사실상 불가능에 가까움.
        - 80~89점: 면접에 부르고 싶은 수준. 구체적 성과, 회사 특화, 진정성이 모두 갖춰져야 함.
        - 70~79점: 괜찮지만 아쉬움이 남는 수준. 개선 여지가 분명히 보임.
        - 60~69점: 평범한 수준. 다른 지원자와 차별화 안 됨. AI가 쓴 티가 남.
        - 50~59점: 기본기 부족. 구체성 없는 추상적 서술 위주.
        - 50점 미만: 질문 의도 파악 실패 또는 전면 재작성 필요.

        [채점 보정 규칙]
        - AI가 생성한 초안은 대부분 55~70점 범위입니다. 첫 초안에 80점 이상은 거의 없습니다.
        - "~했습니다"로 끝나는 문장이 3개 연속이면 해당 항목 -10점.
        - 채용공고 키워드를 단순 나열만 했으면 keywordUsage 최대 60점.
        - 숫자/정량적 성과가 하나도 없으면 specificity 최대 40점.
        - "기여하겠습니다", "성장하겠습니다" 같은 추상적 다짐이 있으면 authenticity -15점.
        - 회사명만 바꾸면 다른 회사에도 쓸 수 있는 내용이면 orgFit 최대 50점.

        [기술 블로그 스타일 보정 규칙]
        - 기술 용어 3개 이상을 맥락(이유/임팩트) 없이 나열 → authenticity -20, aiDetectionRisk +15.
        - 기술 이야기가 전체의 70%% 이상 → orgFit 최대 40.
        - 모든 기술 언급에 "왜 선택했는가" + "비즈니스 임팩트"가 없으면 감점.

        [orgFit 세분화 규칙 — 회사 고유명사 기준]
        - 회사 제품/서비스 고유명사 0개 → orgFit 최대 30점.
        - 회사 제품/서비스 고유명사 1개 → orgFit 최대 50점.
        - 회사 제품/서비스 고유명사 2개 이상 → orgFit 최대 100점.
        (고유명사 = 회사명이 아닌, 해당 회사의 제품/서비스/시스템 이름)

        [추가 보정 규칙]
        - 범용적 첫 문장 ("직장을 선택할 때...", "저는 어릴 때부터...") → answerRelevance -10.
        - 추상적 마무리 ("일하고 싶습니다", "성장하겠습니다") → authenticity -15.

        [평가 항목 — 9개]
        1. 답변적합도 (가중치 10%%): 질문이 묻는 것에 정확히 답했는가? 질문 의도를 벗어나면 0점.
        2. 직무적합도 (가중치 20%%): 당장 투입하면 일할 수 있겠는가? 기술 스택, 유사 경험, 구체적 성과.
        3. 조직적합도 (가중치 15%%): 회사명을 바꾸면 못 쓸 정도로 특화됐는가? 이 회사만의 문화/방향 이해. 회사 제품/서비스 고유명사 필수.
        4. 구체성 (가중치 15%%): 숫자, 프로젝트명, 정량적 성과가 있는가? "열심히 했다"는 0점.
        5. 진정성/개성 (가중치 10%%): 이 사람만의 고유한 이야기인가? 누구나 쓸 수 있는 말이면 0점.
        6. AI탐지 위험도 (가중치 10%%): AI가 쓴 것 같은 패턴이 있는가? (높을수록 위험) 같은 어미 반복, 추상적 미사여구, 정형화된 구조.
        7. 논리적 구조 (가중치 5%%): 기승전결 흐름이 명확한가? 각 단락이 유기적으로 연결되는가?
        8. 키워드 활용 (가중치 10%%): 채용공고의 핵심 키워드가 자연스럽게 녹아있는가?
        9. 경험 일관성 (가중치 5%%): 자소서에 언급된 경험이 [제공된 경험 목록]과 일치하는가? 제공되지 않은 프로젝트, 회사, 수상 경력이 언급되면 0점.

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
            "keywordUsage": 0~100,
            "experienceConsistency": 0~100
          },
          "violations": ["문장 인용 + 구체적 문제 지적", ...],
          "improvements": ["현재 문장 인용 → 개선 방향 → 예시", ...],
          "overallComment": "전체 총평 (2~3문장)"
        }""";

    private final AiPort claudeHaiku;
    private final AiPort claudeSonnet;
    private final ObjectMapper objectMapper;

    public ReviewAgent(@Qualifier("claudeHaiku") AiPort claudeHaiku,
                       @Qualifier("claudeSonnet") AiPort claudeSonnet,
                       ObjectMapper objectMapper) {
        this.claudeHaiku = claudeHaiku;
        this.claudeSonnet = claudeSonnet;
        this.objectMapper = objectMapper;
    }

    public ReviewResult review(String draft, JobPosting jobPosting, String question,
                               int iterationNum, List<UserExperience> providedExperiences) {
        return review(draft, jobPosting, question, iterationNum, providedExperiences, 0);
    }

    public ReviewResult review(String draft, JobPosting jobPosting, String question,
                               int iterationNum, List<UserExperience> providedExperiences,
                               int charLimit) {
        String userPrompt = buildReviewPrompt(draft, jobPosting, question, iterationNum, providedExperiences, charLimit);
        // 1차 리뷰는 Sonnet (개선 방향을 결정하는 가장 중요한 리뷰), 이후는 Haiku
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

    /** 하위호환: experiences 없이 호출하면 경험 목록 없이 검토 */
    public ReviewResult review(String draft, JobPosting jobPosting, String question, int iterationNum) {
        return review(draft, jobPosting, question, iterationNum, List.of());
    }

    private String buildReviewPrompt(String draft, JobPosting jobPosting, String question,
                                      int iterationNum, List<UserExperience> providedExperiences,
                                      int charLimit) {
        String companyAnalysis = jobPosting.getCompanyAnalysis();
        String analysisSection = (companyAnalysis != null && !companyAnalysis.isBlank())
            ? "\n            [회사 심층 분석 — 조직적합도 평가 시 참고]\n            " + companyAnalysis + "\n"
            : "";

        String experienceSection = "";
        if (providedExperiences != null && !providedExperiences.isEmpty()) {
            String expList = providedExperiences.stream()
                .map(e -> "- [%s] %s (%s): %s".formatted(
                    e.getCategory(), e.getTitle(), e.getPeriod(),
                    e.getDescription() != null ? e.getDescription().substring(0, Math.min(100, e.getDescription().length())) : ""
                ))
                .collect(Collectors.joining("\n            "));
            experienceSection = "\n            [제공된 경험 목록 — 경험 일관성 평가 시 참고]\n            "
                + expList + "\n";
        }

        boolean hasExperiences = providedExperiences != null && !providedExperiences.isEmpty();
        String experienceInstruction = hasExperiences
            ? "경험 일관성 평가 시, 자소서에 언급된 경험이 [제공된 경험 목록]에 있는지 대조하세요."
            : "경험 목록이 제공되지 않았으므로 experienceConsistency는 80으로 고정 채점하세요.";

        int actualLength = draft != null ? draft.length() : 0;
        String charLimitSection = charLimit > 0
            ? "\n            [글자수 정보] 실제: %d자 / 제한: %d자. %s\n".formatted(
                actualLength, charLimit,
                actualLength > charLimit ? "글자수 초과! violations에 반드시 지적하세요." : "글자수 준수.")
            : "";

        return """
            [검토 대상 자소서 — %d차 검토]

            [채용공고 정보]
            회사: %s
            직무설명: %s
            자격요건: %s
            %s%s%s
            [자소서 문항]
            %s

            [자소서 내용]
            %s

            위 자소서를 9개 평가 항목으로 채점하고, violations과 improvements를 구체적으로 작성하세요.
            조직적합도 평가 시, 회사 심층 분석 내용이 자소서에 얼마나 반영되었는지를 기준으로 채점하세요.
            %s
            반드시 순수 JSON만 출력하세요.""".formatted(
                iterationNum,
                jobPosting.getCompanyName(),
                jobPosting.getJobDescription() != null ? jobPosting.getJobDescription() : "",
                jobPosting.getRequirements() != null ? jobPosting.getRequirements() : "",
                analysisSection,
                experienceSection,
                charLimitSection,
                question != null ? question : "(단일 자소서)",
                draft,
                experienceInstruction
            );
    }

    private ReviewResult parseReviewResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            JsonNode scoresNode = root.get("scores");
            int experienceConsistency = scoresNode.has("experienceConsistency")
                ? scoresNode.get("experienceConsistency").asInt() : 80;

            ReviewResult.Scores scores = new ReviewResult.Scores(
                scoresNode.get("answerRelevance").asInt(),
                scoresNode.get("jobFit").asInt(),
                scoresNode.get("orgFit").asInt(),
                scoresNode.get("specificity").asInt(),
                scoresNode.get("authenticity").asInt(),
                scoresNode.get("aiDetectionRisk").asInt(),
                scoresNode.get("logicalStructure").asInt(),
                scoresNode.get("keywordUsage").asInt(),
                experienceConsistency
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
