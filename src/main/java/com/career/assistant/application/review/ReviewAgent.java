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
        - "리더십 발휘", "뛰어난 소통력", "열정적으로 임했습니다" 등 추상적 역량 주장 뒤에 구체적 장면(언제/어디서/무엇을)이 없으면 authenticity -20점.
        - 누구나 할 수 있는 흔한 경험(동아리 활동, 학회, 일반 팀프로젝트)이 차별화 없이 서술되면 authenticity 최대 40점.

        [직무·회사 맞춤도 특별 검증]
        - 채용공고 자격요건 상위 3개를 식별하고, 자소서에서 각각에 대한 구체적 증거가 있는지 확인. 1개 이하면 jobFit 최대 50점.
        - 회사 고유명사(제품/서비스/시스템명)가 지원자 경험과 연결되어 사용되었는지 확인. 단순 나열이면 orgFit -15점.
        - "이 자소서의 회사명을 바꾸면 다른 회사에도 쓸 수 있는가?" 테스트. 가능하면 orgFit 최대 40점.
        - 같은 직무 지원자 100명이 비슷하게 쓸 수 있는 내용이면 authenticity 최대 45점.

        [사고 흐름 검증 — 행위 나열(STAR) vs 판단 근거]
        - 경험이 "~했습니다"의 행위 나열로만 구성되면 authenticity 최대 45, specificity -15. 성과 수치가 있어도 "왜 그 판단을 했는지" 없으면 성과 재현 가능성을 증명 못함.
        - 합격 수준: "문제 본질 분석 → 전략적 판단(근거) → 실행 중 변수 발견 → 전략 재수정 → 성과"의 사고 흐름이 보여야 함.
        - "왜 이 방법을 선택했는지", "왜 전략을 수정했는지"의 판단 근거가 1개 이상 있으면 authenticity +10.

        [기술 블로그 스타일 보정 규칙]
        - 기술 용어 3개 이상을 맥락(이유/임팩트) 없이 나열 → authenticity -20, aiDetectionRisk +15.
        - 기술 이야기가 전체의 70% 이상 → orgFit 최대 40.
        - 모든 기술 언급에 "왜 선택했는가" + "비즈니스 임팩트"가 없으면 감점.

        [orgFit 세분화 규칙 — 회사 고유명사 기준]
        - 회사 제품/서비스 고유명사 0개 → orgFit 최대 30점.
        - 회사 제품/서비스 고유명사 1개 → orgFit 최대 50점.
        - 회사 제품/서비스 고유명사 2개 이상 → orgFit 최대 100점.
        (고유명사 = 회사명이 아닌, 해당 회사의 제품/서비스/시스템 이름)
        - 핵심 검증: 고유명사가 등장하더라도 "내 경험이 그것과 어떻게 맞닿는지" 연결이 없으면 orgFit -15. 이름만 던지고 연결 없으면 "아는 척"이지 회사 이해가 아님.
        - 지원동기 문항에서 "왜 이 회사인가"보다 "왜 이 직무에 관심이 있는가"가 더 강하게 읽히면 orgFit 최대 45점. 직무 관심사 ≠ 회사 지원동기.

        [AI 문체 탐지 강화 규칙]
        - 모든 문장이 비슷한 길이·비슷한 어미로 정교하게 압축되어 있으면 aiDetectionRisk +20. "잘 만든 답안"은 사람이 쓴 글이 아님.
        - 구어체 전환어("솔직히", "돌이켜보면", "그때는 몰랐지만")가 하나도 없으면 aiDetectionRisk +10.
        - 기술 블로그 요약문처럼 읽히면 authenticity 최대 50. 자소서는 기술 보고서가 아니라 "이 사람과 일하고 싶은지" 판단하는 문서.

        [문항 유형별 가중치 조정]
        - 지원동기 문항: 4단계 연결(산업→회사→직무→경험) 중 누락이 있으면 orgFit에서 단계당 -15점. 산업 이야기만 하고 이 회사 특징이 없으면 orgFit 최대 30점.
        - 장단점 문항: 단점을 장점으로 포장하면("일을 너무 열심히 한다" 등) answerRelevance 최대 30점. 형식적·피상적 답변이면 authenticity 최대 40점. 단점 관리의 구체적 행동이 없으면 specificity -20점.

        [감점 요소 우선 탐지 — 아래 항목 발견 시 즉시 violations에 기록]
        1. 기업명 오기재: 지원 회사와 다른 회사명이 등장하면 orgFit 0점 + violations 최우선 기록.
        2. 복붙 흔적: "귀사", "해당 회사" 등 특정 회사를 지칭하지 않는 범용 표현이 2개 이상이면 orgFit -20.
        3. 표현 반복: 동일 어미·접속사가 3회 이상 연속 반복되면 aiDetectionRisk +15.
        4. 맞춤법/띄어쓰기 오류: 발견 즉시 violations에 기록.
        5. 직무 무관 경험 과다: 자소서의 50% 이상이 지원 직무와 무관한 경험이면 jobFit 최대 40점.
        6. 근거 없는 과장: "업계 최초", "획기적", "혁신적" 등 검증 불가 수식어가 구체적 수치/출처 없이 사용되면 authenticity -15.

        [첫 문장 후킹 검증 — 서류 합격의 핵심]
        채용담당자는 하루 수백 건을 보므로 첫 문장에서 승부가 갈립니다.
        - 즉시 탈락 첫 문장: "성실합니다", "배우겠습니다", "지원하게 된 계기는", "저는 어릴 때부터", "직장을 선택할 때", "안녕하세요 저는" → answerRelevance -15, authenticity -10.
        - 첫 두 문장에 [구체적 숫자/장면] + [이 회사와의 접점]이 없으면 answerRelevance -10.
        - 좋은 첫 문장: 읽자마자 "이 사람이 어떤 사람인지"가 느껴지고, "왜 우리 회사에 맞는지" 궁금해지는 문장.

        [관점 전환 검증]
        - "내가 하고 싶은 말" 중심이면 감점, "기업이 듣고 싶은 말" 중심이면 가점.
        - 채용공고의 Pain Point(기업이 현재 고민하는 과제)를 짚고 그것을 풀 수 있음을 보여주는가? 없으면 orgFit -10, jobFit -10.
        - "이 사람을 뽑으면 우리 팀에 이런 이득이 있겠다"가 읽히는가? 안 읽히면 jobFit 최대 55점.

        [추가 보정 규칙]
        - 추상적 마무리 ("일하고 싶습니다", "성장하겠습니다") → authenticity -15.

        [평가 항목 — 9개]
        1. 답변적합도 (가중치 10%): 질문이 묻는 것에 정확히 답했는가? 질문 의도를 벗어나면 0점.
        2. 직무적합도 (가중치 20%): 당장 투입하면 일할 수 있겠는가? 기술 스택, 유사 경험, 구체적 성과.
        3. 조직적합도 (가중치 20%): 회사명을 바꾸면 못 쓸 정도로 특화됐는가? 이 회사만의 문화/방향 이해. 회사 제품/서비스 고유명사 필수.
        4. 구체성 (가중치 15%): 숫자, 프로젝트명, 정량적 성과가 있는가? "열심히 했다"는 0점.
        5. 진정성/개성 (가중치 10%): 이 사람만의 고유한 이야기인가? 누구나 쓸 수 있는 말이면 0점.
        6. AI탐지 위험도 (가중치 10%): AI가 쓴 것 같은 패턴이 있는가? (높을수록 위험) 같은 어미 반복, 추상적 미사여구, 정형화된 구조.
        7. 논리적 구조 (가중치 3%): 기승전결 흐름이 명확한가? 각 단락이 유기적으로 연결되는가? "각 문단의 한 줄 요약이 가능한가?" — 한 줄 요약이 불가능한 문단이 있으면 -15점.
        8. 키워드 활용 (가중치 10%): 채용공고의 핵심 키워드가 자연스럽게 녹아있는가?
        9. 경험 일관성 (가중치 2%): 자소서에 언급된 경험이 [제공된 경험 목록]과 일치하는가? 제공되지 않은 프로젝트, 회사, 수상 경력이 언급되면 0점.

        [8대 평가 기준 ↔ 9개 세부 점수 매핑]
        아래 8대 평가 기준을 반드시 점검하고, 해당 세부 점수에 반영하세요:
        1. 입사지원분야 경쟁력 → jobFit, keywordUsage
        2. 회사 분석 → orgFit (고유명사 2개 이상 필수)
        3. 진부한 표현 없음 → authenticity, aiDetectionRisk
        4. 구체적 경험 → specificity, experienceConsistency
        5. 필요항목 빠짐없이 → answerRelevance
        6. 간결하고 명료 → logicalStructure
        7. 맞춤법/띄어쓰기 → violations에 지적
        8. 열정 → orgFit, authenticity

        [피드백 규칙]
        - violations: 반드시 문장을 인용하고 구체적 문제를 지적하세요. "좀 더 구체적으로" 같은 모호한 피드백 금지.
        - improvements: 현재 문장을 인용 → 문제점 → 개선 방향 → 개선 예시를 포함하세요.
        - overallComment에서 8대 평가 기준 중 부족한 항목을 번호와 함께 명시적으로 언급하세요.
          예: "기준 2(회사 분석) — 고유명사가 1개뿐입니다. 기준 4(구체적 경험) — 정량적 수치가 부족합니다."
        - overallComment 마지막에 반드시 최종 판단을 추가하세요: "면접 초대 여부: YES/NO — (근거 1문장)". "이 글을 읽고 면접에 불러볼 이유가 생기는가?"를 기준으로 판단.

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
          "overallComment": "전체 총평 (2~3문장). 8대 평가 기준 중 부족한 항목을 번호와 함께 명시."
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
        String jobContext = buildJobContext(jobPosting);
        String userPrompt = buildReviewPrompt(draft, jobPosting, question, iterationNum, providedExperiences, charLimit);
        // 1차 리뷰는 Sonnet (개선 방향 결정), 2차+ Haiku (확인 점검)
        AiPort reviewer = (iterationNum == 1) ? claudeSonnet : claudeHaiku;

        log.info("[에이전트] {}차 검토 — 모델: {}", iterationNum, reviewer.getModelName());
        try {
            String response = reviewer.generate(REVIEWER_SYSTEM_PROMPT, jobContext, userPrompt);
            return parseReviewResponse(response);
        } catch (Exception e) {
            throw new ReviewGenerationException(
                "리뷰 생성 실패 (%d차, 모델: %s)".formatted(iterationNum, reviewer.getModelName()), e);
        }
    }

    /** 하위호환: experiences 없이 호출하면 경험 목록 없이 검토 */
    public ReviewResult review(String draft, JobPosting jobPosting, String question, int iterationNum) {
        return review(draft, jobPosting, question, iterationNum, List.of());
    }

    /** 동일 공고 내 반복 호출 시 캐시되는 안정적 컨텍스트 */
    private String buildJobContext(JobPosting jobPosting) {
        String companyAnalysis = jobPosting.getCompanyAnalysis();
        String analysisSection = (companyAnalysis != null && !companyAnalysis.isBlank())
            ? "\n[회사 심층 분석 — 조직적합도 평가 시 참고]\n" + companyAnalysis + "\n"
            : "";

        return """
            [채용공고 정보]
            회사: %s
            직무설명: %s
            자격요건: %s
            %s""".formatted(
                jobPosting.getCompanyName(),
                jobPosting.getJobDescription() != null ? jobPosting.getJobDescription() : "",
                jobPosting.getRequirements() != null ? jobPosting.getRequirements() : "",
                analysisSection
            );
    }

    private String buildReviewPrompt(String draft, JobPosting jobPosting, String question,
                                      int iterationNum, List<UserExperience> providedExperiences,
                                      int charLimit) {
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
            %s%s
            [자소서 문항]
            %s

            [자소서 내용]
            %s

            위 자소서를 9개 평가 항목으로 채점하고, violations과 improvements를 구체적으로 작성하세요.
            조직적합도 평가 시, 회사 심층 분석 내용이 자소서에 얼마나 반영되었는지를 기준으로 채점하세요.
            %s
            반드시 순수 JSON만 출력하세요.""".formatted(
                iterationNum,
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

            JsonNode scoresNode = root.path("scores");
            if (scoresNode.isMissingNode() || !scoresNode.isObject()) {
                log.warn("[에이전트] 검토 결과에 scores 객체 없음 — 폴백 적용");
                return ReviewResult.fallback();
            }

            ReviewResult.Scores scores = new ReviewResult.Scores(
                scoresNode.path("answerRelevance").asInt(50),
                scoresNode.path("jobFit").asInt(50),
                scoresNode.path("orgFit").asInt(50),
                scoresNode.path("specificity").asInt(50),
                scoresNode.path("authenticity").asInt(50),
                scoresNode.path("aiDetectionRisk").asInt(50),
                scoresNode.path("logicalStructure").asInt(50),
                scoresNode.path("keywordUsage").asInt(50),
                scoresNode.path("experienceConsistency").asInt(80)
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
