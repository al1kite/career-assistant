package com.career.assistant.application;

import com.career.assistant.domain.experience.UserExperience;
import com.career.assistant.domain.jobposting.CompanyType;
import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.infrastructure.crawling.EssayQuestion;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CoverLetterPromptBuilder {

    private static final ObjectMapper LENIENT_MAPPER = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 동일 공고 내 반복 호출 시 캐시되는 안정적 컨텍스트 (회사분석 + 채용공고) */
    public String buildJobContext(JobPosting jobPosting) {
        String companyAnalysis = buildCompanyAnalysisGuide(jobPosting);
        String tone = resolveTone(jobPosting.getCompanyType());

        return """
            [기업 분석]
            %s

            [톤]
            %s

            [채용공고]
            회사: %s
            직무설명: %s
            자격요건: %s""".formatted(
                companyAnalysis,
                tone,
                jobPosting.getCompanyName(),
                jobPosting.getJobDescription() != null ? jobPosting.getJobDescription() : "(정보 없음)",
                jobPosting.getRequirements() != null ? jobPosting.getRequirements() : "(정보 없음)"
            );
    }

    public String build(JobPosting jobPosting, List<UserExperience> experiences) {
        return build(jobPosting, experiences, 1000);
    }

    public String build(JobPosting jobPosting, List<UserExperience> experiences, int charLimit) {
        String experienceSummary = experiences.stream()
            .map(this::formatExperience)
            .collect(Collectors.joining("\n\n"));

        String jobRoleDirective = buildJobRoleDirective(jobPosting, experiences);
        int targetMin = (int) (charLimit * 0.9);

        return """
            아래 채용공고와 지원자 경험을 바탕으로, 이 회사에만 쓸 수 있는 자소서를 작성하세요.

            [글자수 규칙]
            반드시 %d자 이내. 목표 범위: %d자 ~ %d자.
            %s
            [절대 금지 사항]
            1. 범용적 서술 금지: 회사명을 바꿔도 그대로 쓸 수 있는 문장은 전부 삭제하고 다시 쓰세요.
            2. 증거 없는 역량 주장 금지: "리더십 발휘", "뛰어난 소통력", "열정적으로 임했습니다" 등 추상적 역량을 주장하면서 구체적 장면이 없으면 삭제하세요.
            3. 흔한 경험 차별화 필수: 누구나 할 수 있는 경험(동아리, 학회, 팀프로젝트)을 서술할 때는 반드시 "이 사람만의 차별화된 관점·결과"를 포함하세요. 차별화 없으면 다른 경험을 선택하세요.
            4. 과장 금지: 근거 없이 성과를 부풀리면 면접에서 검증됩니다. 실제 수치와 역할만 정확히 서술하세요.
            5. 회사 고유명사 나열 금지: 회사 제품/서비스명을 언급할 때 반드시 "내 경험이 그것과 어떻게 맞닿는지"를 한 문장 더 써야 합니다. 이름만 던지면 "아는 척"으로 읽힙니다.
            6. 신입은 현실적 목표 제시: 신입/주니어라면 "입사 후 3개월 목표"는 학습·적응 중심으로. 현직자도 어려운 수치 목표를 첫 날부터 제시하면 뜬 소리로 읽힙니다.

            [문체 규칙 — 사람이 쓴 글처럼]
            - 모든 문장을 같은 길이·같은 어미로 쓰지 마세요. 긴 문장 뒤에 짧은 문장, 서술 뒤에 독백이나 질문을 섞으세요.
            - 기술 용어를 3개 이상 연속 나열하지 마세요. 기술은 "왜 그것을 선택했는지" 맥락 안에서 자연스럽게 녹이세요.
            - "솔직히", "돌이켜보면", "그때는 몰랐지만" 같은 구어체 전환어를 1~2회 사용하세요.
            - 너무 정교하게 압축하지 마세요. 약간의 여백과 호흡이 있는 글이 신뢰감을 줍니다.

            [관점 전환 — 가장 중요한 원칙]
            "내가 하고 싶은 말"이 아니라 "기업이 듣고 싶은 말"을 쓰세요.
            채용공고에는 실무진이 고민하는 Pain Point가 담겨 있습니다. 그 Pain Point를 짚고, 내가 그것을 풀 수 있는 사람임을 증명하세요.
            실천법: (1) 채용공고에서 반복되는 키워드 3개 추출 (2) 그 키워드가 가리키는 실무진의 Pain Point 추론 (3) 내 경험 중 그 Pain Point를 풀어본 사례 선택 (4) "나는 이걸 할 수 있습니다"가 아닌 "이 문제를 이렇게 풀어봤습니다"로 서술

            [첫 문장 설계 — 3초 안에 "궁금한데?" 만들기]
            채용담당자는 하루 수백 개의 자소서를 봅니다. 첫 문장에서 승부가 갈립니다.
            - 금지 패턴: "성실합니다", "배우겠습니다", "지원하게 된 계기는", "저는 어릴 때부터", "직장을 선택할 때"
            - 후킹 공식: [업무 습관/캐릭터 라벨] + [구체적 숫자/장면] + [이 회사와의 접점]
            - 예시: "장애 알림이 오면 새벽 4시에도 대시보드부터 켭니다. 47일간 XX시스템 안정화율을 94%에서 99.2%로 끌어올린 습관입니다."
            - 핵심: 첫 두 문장만 읽어도 "이 사람이 어떤 사람인지" + "왜 우리 회사에 맞는지" 느껴져야 합니다.

            [경험 서술 — 행위 나열(STAR) 금지, 사고 흐름 중심]
            경험을 "무엇을 했다 → 결과 나왔다"로 쓰면 나보다 좋은 스펙의 지원자에게 집니다.
            반드시 "왜 그 판단을 했는지(판단 근거)" + "전략을 어떻게 수정했는지(사고 고도화)"를 보여주세요.
            구조: 문제의 본질 규명(왜 실패/왜 어려운지 심층 분석) → 전략적 판단(근거 있는 방향 전환) → 전략 고도화(실행 중 발견한 변수로 재수정) → 성과.
            이렇게 쓰면 면접관이 "이 사람은 다른 문제에도 같은 사고력을 발휘하겠다"고 판단합니다.

            [판단 근거 병기 — 모든 행동 문장에 적용]
            행동/결정 문장을 쓸 때마다 바로 뒤에 "왜?"를 붙이세요.
            나쁜 예: "Redis 캐시를 도입했습니다. 그 결과 응답 시간이 개선되었습니다."
            좋은 예: "Redis 캐시를 도입했습니다. DB 조회 로그를 분석하니 동일 쿼리가 하루 12만 건 반복되고 있었기 때문입니다. 응답 시간이 2.3초에서 0.4초로 줄었습니다."
            면접관은 행동보다 판단 근거를 봅니다. 근거 없는 행동 나열은 "시키면 하는 사람"으로 읽힙니다.

            [경험 재구성 — 회사 관점]
            같은 경험이라도 이 회사의 Pain Point에 맞게 재구성하세요.
            "내가 한 일"을 나열하지 말고, "이 회사의 과제를 내가 풀어본 적 있다"는 구조로 서술하세요.
            기업분석에서 제공된 painPoints, hiddenRequirements, insiderLanguage를 경험 서술에 자연스럽게 녹이세요.

            [기승전결 4단락 구성 — 제목 없이]
            기(起) (약 150자) — 이 회사의 Pain Point나 사업 과제를 짚으며 시작. 후킹 공식 적용. "지원하게 된 계기는~" 금지.
            승(承) (약 300자) — 핵심 경험의 사고 흐름을 서술. 문제의 본질 분석(왜 안 됐는지) → 전략적 판단(왜 이 방법을 선택했는지 근거) → 실행 중 변수 발견 → 전략 재수정. "했습니다"의 나열이 아닌 "왜 그랬는지"의 연쇄.
            전(轉) (약 350자) — 합격을 결정하는 단락. 회사의 Pain Point를 명시하고 내 사고 방식이 그것을 풀 수 있음을 증명. 성과 재현 가능성을 보여주세요.
            결(結) (약 200자) — 이 회사에서 구체적으로 풀고 싶은 문제 제시. "열심히 하겠습니다"가 아닌 "OO의 XX를 YY 방법으로 개선하겠다" 수준. Pain Point와 연결.

            [지원자 경험]
            %s""".formatted(
                charLimit, targetMin, charLimit,
                jobRoleDirective,
                experienceSummary
            );
    }

    public String buildForQuestion(JobPosting jobPosting, List<UserExperience> experiences,
                                    EssayQuestion question) {
        UserExperience primary = experiences == null || experiences.isEmpty() ? null : experiences.get(0);
        List<UserExperience> secondary = experiences == null || experiences.size() <= 1
            ? List.of()
            : experiences.subList(1, experiences.size());
        return buildForQuestion(jobPosting, primary, secondary, question);
    }

    public String buildForQuestion(JobPosting jobPosting, UserExperience primary,
                                    List<UserExperience> secondary, EssayQuestion question) {
        return buildForQuestion(jobPosting, primary, secondary, question, null);
    }

    public String buildForQuestion(JobPosting jobPosting, UserExperience primary,
                                    List<UserExperience> secondary, EssayQuestion question,
                                    String masterPlan) {
        String experienceSummary = formatQuestionExperiences(primary, secondary);

        List<UserExperience> allExperiences = new java.util.ArrayList<>();
        if (primary != null) allExperiences.add(primary);
        if (secondary != null) allExperiences.addAll(secondary);

        String questionType = classifyQuestionType(question.questionText());
        String typeGuide = getTypeGuide(questionType);
        String questionGuide = buildQuestionGuide(jobPosting, question);
        String masterPlanSection = buildMasterPlanSection(masterPlan, question.number());
        String jobRoleDirective = buildJobRoleDirective(jobPosting, allExperiences);
        int charLimit = question.charLimit() > 0 ? question.charLimit() : 1000;
        int targetMin = (int) (charLimit * 0.9);

        return """
            아래 자소서 문항에 대한 답변을 작성하세요.

            [글자수 규칙]
            반드시 %d자 이내. 목표 범위: %d자 ~ %d자.
            %s
            [절대 금지 사항]
            1. 범용적 서술 금지: 회사명을 바꿔도 그대로 쓸 수 있는 문장은 전부 삭제하고 다시 쓰세요.
            2. 증거 없는 역량 주장 금지: "리더십 발휘", "뛰어난 소통력" 등 추상적 역량 주장 뒤에 구체적 장면이 없으면 삭제하세요.
            3. 흔한 경험 차별화 필수: 누구나 할 수 있는 경험은 반드시 차별화된 관점·결과를 포함하세요.
            4. 과장 금지: 근거 없는 성과 부풀리기는 면접에서 탈락 사유가 됩니다.
            5. 회사 고유명사 나열 금지: 제품/서비스명을 언급하면 반드시 "내 경험이 그것과 어떻게 맞닿는지" 한 문장 추가. 이름만 던지면 "아는 척"으로 읽힙니다.
            6. 신입은 현실적 목표: 현직자도 어려운 수치 목표를 신입이 첫 날부터 제시하면 뜬 소리입니다.

            [문체 규칙 — 사람이 쓴 글처럼]
            - 문장 길이와 어미를 변화시키세요. 긴 문장 뒤에 짧은 문장, 서술 뒤에 독백을 섞으세요.
            - 기술 용어 3개 이상 연속 나열 금지. "왜 그것을 선택했는지" 맥락 안에서 녹이세요.
            - "솔직히", "돌이켜보면" 같은 구어체 전환어를 1~2회 사용하세요.
            - 너무 정교하게 압축하지 마세요. 약간의 여백과 호흡이 신뢰감을 줍니다.

            [문항 %d번]
            %s
            %s
            [이 문항 전용 작성 가이드]
            %s

            [문항 유형: %s]
            %s

            [관점 전환 — 가장 중요한 원칙]
            "내가 하고 싶은 말"이 아니라 "기업이 듣고 싶은 말"을 쓰세요.
            실천법: (1) 채용공고에서 반복되는 키워드 3개 추출 (2) 그 키워드가 가리키는 실무진의 Pain Point 추론 (3) 내 경험 중 그 Pain Point를 풀어본 사례 선택 (4) "나는 이걸 할 수 있습니다"가 아닌 "이 문제를 이렇게 풀어봤습니다"로 서술

            [첫 문장 설계 — 3초 안에 "궁금한데?" 만들기]
            - 금지 패턴: "성실합니다", "배우겠습니다", "지원하게 된 계기는", "저는 어릴 때부터"
            - 후킹 공식: [업무 습관/캐릭터 라벨] + [구체적 숫자/장면] + [이 회사와의 접점]
            - 첫 두 문장만 읽어도 "이 사람이 어떤 사람인지" + "왜 우리 회사에 맞는지" 느껴져야 합니다.

            [경험 서술 — 행위 나열(STAR) 금지, 사고 흐름 중심]
            "무엇을 했다"가 아니라 "왜 그 판단을 했는지"를 보여주세요.
            구조: 문제 본질 규명 → 전략적 판단(근거) → 실행 중 변수 발견 → 전략 재수정 → 성과.
            면접관이 "이 사람은 성과를 재현할 수 있겠다"고 느끼게 하세요.

            [판단 근거 병기 — 모든 행동 문장에 적용]
            행동/결정 문장을 쓸 때마다 바로 뒤에 "왜?"를 붙이세요.
            나쁜 예: "Redis 캐시를 도입했습니다. 그 결과 응답 시간이 개선되었습니다."
            좋은 예: "Redis 캐시를 도입했습니다. DB 조회 로그를 분석하니 동일 쿼리가 하루 12만 건 반복되고 있었기 때문입니다."
            근거 없는 행동 나열은 "시키면 하는 사람"으로 읽힙니다.

            [경험 재구성 — 회사 관점]
            같은 경험이라도 이 회사의 Pain Point에 맞게 재구성하세요.
            "내가 한 일"을 나열하지 말고, "이 회사의 과제를 내가 풀어본 적 있다"는 구조로 서술하세요.

            [기승전결 서사 구조]
            기(起): 회사의 Pain Point나 문항 핵심 주제를 짚으며 시작. 후킹 공식 적용.
            승(承): 경험의 사고 흐름 서술. 문제 본질 분석(왜 안 됐는지) → 전략적 판단(근거) → 실행 중 변수 → 전략 재수정. "했습니다" 나열이 아닌 "왜"의 연쇄.
            전(轉) **(합격을 결정하는 단락)**: 회사의 Pain Point를 명시하고 내 사고 방식이 그것을 풀 수 있음을 증명. 회사 고유명사를 내 경험과 엮어 사용.
            결(結): 위 [문항 유형] 가이드의 결(結) 지침을 따르세요. 추상적 다짐이 아닌, 구체적 문제·액션·기준을 제시. (전(轉)에 가장 많은 비중)

            [지원자 경험]
            %s""".formatted(
                charLimit, targetMin, charLimit,
                jobRoleDirective,
                question.number(),
                question.questionText(),
                masterPlanSection,
                questionGuide.isBlank() ? "(분석 데이터 없음 — 채용공고에서 직접 파악하세요)" : questionGuide,
                questionType,
                typeGuide,
                experienceSummary
            );
    }

    public String buildImprovementPrompt(JobPosting jobPosting, List<UserExperience> experiences,
                                           String question, String previousDraft,
                                           String reviewFeedbackJson, int iterationNum) {
        return buildImprovementPrompt(jobPosting, experiences, question, previousDraft,
            reviewFeedbackJson, iterationNum, null, 1000, null);
    }

    public String buildImprovementPrompt(JobPosting jobPosting, List<UserExperience> experiences,
                                           String question, String previousDraft,
                                           String reviewFeedbackJson, int iterationNum,
                                           String targetedStrategy) {
        return buildImprovementPrompt(jobPosting, experiences, question, previousDraft,
            reviewFeedbackJson, iterationNum, targetedStrategy, 1000, null);
    }

    public String buildImprovementPrompt(JobPosting jobPosting, List<UserExperience> experiences,
                                           String question, String previousDraft,
                                           String reviewFeedbackJson, int iterationNum,
                                           String targetedStrategy, int charLimit,
                                           String userMessage) {
        UserExperience primary = experiences == null || experiences.isEmpty() ? null : experiences.get(0);
        List<UserExperience> secondary = experiences == null || experiences.size() <= 1
            ? List.of()
            : experiences.subList(1, experiences.size());
        return buildImprovementPrompt(
            jobPosting, primary, secondary, question, previousDraft, reviewFeedbackJson,
            iterationNum, targetedStrategy, charLimit, userMessage
        );
    }

    public String buildImprovementPrompt(JobPosting jobPosting, UserExperience primary,
                                           List<UserExperience> secondary, String question, String previousDraft,
                                           String reviewFeedbackJson, int iterationNum,
                                           String targetedStrategy, int charLimit,
                                           String userMessage) {
        String experienceSummary = formatQuestionExperiences(primary, secondary);

        List<UserExperience> allExperiences = new java.util.ArrayList<>();
        if (primary != null) allExperiences.add(primary);
        if (secondary != null) allExperiences.addAll(secondary);

        String jobRoleDirective = buildJobRoleDirective(jobPosting, allExperiences);

        String userMessageSection = "";
        if (userMessage != null && !userMessage.isBlank()) {
            userMessageSection = "\n            [사용자 추가 지시사항]\n            " + userMessage + "\n";
        }

        String improvementSection;
        if (targetedStrategy != null && !targetedStrategy.isBlank()) {
            improvementSection = """
            %s

            [추가 원칙]
            1. 잘 쓴 부분은 반드시 유지하거나 더 강화하세요.
            2. violations에 지적된 문장은 반드시 수정하세요.
            3. improvements에 제시된 개선 방향과 예시를 적극 참고하세요.
            4. 기술 용어를 지나치게 나열하지 말고, 의사결정 과정과 협업 맥락을 강조하세요.""".formatted(targetedStrategy);
        } else {
            improvementSection = """
            [개선 원칙 — %d차 개선]
            1. 잘 쓴 부분은 반드시 유지하거나 더 강화하세요.
            2. violations에 지적된 문장은 반드시 수정하세요.
            3. improvements에 제시된 개선 방향과 예시를 적극 참고하세요.
            4. AI탐지 위험도가 높다면: 추상적 표현을 구체적으로 바꾸고, 어미 반복을 깨고, 감정과 생생한 디테일을 추가하세요.
            5. 구체성 점수가 낮다면: 숫자, 프로젝트명, KPI, 정량적 성과를 반드시 추가하세요.
            6. 조직적합도가 낮다면: 이 회사만의 특성, 문화, 기술 방향을 더 구체적으로 언급하세요.
            7. 키워드 활용이 낮다면: 채용공고의 핵심 키워드를 자연스럽게 녹이세요.
            8. 기술 용어를 지나치게 나열하지 말고, 의사결정 과정과 협업 맥락을 강조하세요.""".formatted(iterationNum);
        }

        return """
            당신은 채용팀장의 피드백을 받고 자소서를 개선하는 전문가입니다.
            아래의 검토 피드백을 꼼꼼히 반영하여 자소서를 개선하세요.

            [최우선 규칙]
            순수 텍스트만 출력. 마크다운, 소제목, 번호, 불릿 전부 금지.
            단락 사이 빈 줄 하나로 구분. 자소서 본문만 출력.
            반드시 %d자 이내로 작성하세요. (공백 포함, 초과 절대 금지)
            %s
            %s
            %s
            [검토 피드백 (채용팀장)]
            %s

            [이전 초안]
            %s

            [자소서 문항]
            %s

            [지원자 경험]
            %s

            위 피드백을 모두 반영하여 개선된 자소서를 작성하세요. 주력 경험을 중심으로 유지하되, 피드백에 따라 서술을 개선하세요. 자소서 본문만 출력하세요.""".formatted(
                charLimit,
                userMessageSection,
                improvementSection,
                jobRoleDirective,
                reviewFeedbackJson,
                previousDraft,
                question != null ? question : "(단일 자소서)",
                experienceSummary
            );
    }

    private String buildCompanyAnalysisGuide(JobPosting jobPosting) {
        String analysisJson = jobPosting.getCompanyAnalysis();

        // 분석 데이터가 있으면 실제 데이터 기반으로 가이드 생성
        if (analysisJson != null && !analysisJson.isBlank()) {
            try {
                JsonNode analysis = objectMapper.readTree(analysisJson);
                StringBuilder guide = new StringBuilder();

                guide.append("대상 회사: ").append(jobPosting.getCompanyName()).append("\n\n");

                appendAnalysisField(guide, analysis, "companyOverview", "회사 개요");
                appendAnalysisObjectArray(guide, analysis, "coreProducts", "핵심 제품/서비스", "name", "description");
                appendAnalysisField(guide, analysis, "competitiveAdvantage", "경쟁 우위");
                appendAnalysisObjectArray(guide, analysis, "competitors", "주요 경쟁사", "name", "differentiation");
                appendAnalysisField(guide, analysis, "hiringReason", "채용 배경 (이 포지션을 뽑는 이유)");
                appendAnalysisField(guide, analysis, "idealCandidate", "이상적 지원자 프로필");
                appendAnalysisField(guide, analysis, "companyValues", "핵심 가치/문화");
                appendAnalysisField(guide, analysis, "techDirection", "기술 방향성/투자 동향");
                appendAnalysisField(guide, analysis, "techStack", "기술 스택");
                appendAnalysisField(guide, analysis, "painPoints", "Pain Points (이 회사가 가장 고민하는 과제)");
                appendAnalysisField(guide, analysis, "businessChallenges", "현재 사업/기술 과제");
                appendAnalysisField(guide, analysis, "hiddenRequirements", "숨겨진 필수 역량 (우대사항 이면)");
                appendAnalysisField(guide, analysis, "idealCandidateProfile", "합격자 페르소나");
                appendAnalysisField(guide, analysis, "insiderLanguage", "현직자 언어 (자소서에 녹일 표현)");
                appendAnalysisField(guide, analysis, "recentNews", "최근 주요 뉴스/발표");
                appendAnalysisField(guide, analysis, "recentTrends", "최근 동향/전략");

                guide.append("\n위 분석 내용을 자소서 곳곳에 자연스럽게 녹이세요. ");
                guide.append("회사명을 바꾸면 쓸 수 없는, 오직 이 회사에만 해당하는 자소서를 쓰세요.");

                return guide.toString();
            } catch (Exception e) {
                log.warn("회사 분석 JSON 파싱 실패, 폴백 가이드 사용", e);
            }
        }

        // 폴백: 기존 지시문 기반 가이드
        return buildFallbackAnalysisGuide(jobPosting);
    }

    private String buildFallbackAnalysisGuide(JobPosting jobPosting) {
        String companyName = jobPosting.getCompanyName() != null ? jobPosting.getCompanyName() : "해당 회사";
        String jobDesc = jobPosting.getJobDescription() != null ? jobPosting.getJobDescription() : "";

        StringBuilder guide = new StringBuilder();
        guide.append("대상 회사: ").append(companyName).append("\n");
        guide.append("아래 채용공고 내용을 깊이 분석하여 다음을 파악하고 자소서에 반영하세요:\n");
        guide.append("1. 이 회사가 지금 왜 이 포지션을 채용하는가? (채용공고의 직무설명에서 추론)\n");
        guide.append("2. 이 직무가 회사의 어떤 비즈니스 목표와 연결되는가?\n");
        guide.append("3. 채용공고에서 이 회사만의 경쟁 우위, 핵심 사업, 기술적 특징은 무엇인가?\n");
        guide.append("4. 우대사항과 자격요건에서 드러나는 이 회사가 진짜 원하는 인재상은?\n");
        guide.append("5. 경쟁사가 아닌 이 회사를 선택해야 하는 지원자만의 이유는?\n");

        if (jobDesc.length() > 100) {
            guide.append("\n[채용공고에서 추출할 핵심 키워드를 반드시 자소서에 포함하세요]");
        }

        return guide.toString();
    }

    private String buildMasterPlanSection(String masterPlanJson, int questionNumber) {
        if (masterPlanJson == null || masterPlanJson.isBlank()) return "";

        try {
            // markdown fence 제거 + 관용 파싱 (AI 생성 JSON의 trailing comma 등 허용)
            String cleaned = masterPlanJson.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("```(?:json)?\\s*", "");
                cleaned = cleaned.replaceFirst("\\s*```$", "");
            }
            JsonNode plan = LENIENT_MAPPER.readTree(cleaned);
            StringBuilder sb = new StringBuilder();
            sb.append("\n[전체 자소서 전략 — 반드시 따를 것]\n");

            // 서사 테마
            String theme = plan.path("narrativeTheme").asText("");
            if (!theme.isBlank()) {
                sb.append("서사 테마: ").append(theme).append("\n");
            }

            // 어필 포인트
            JsonNode appealPoints = plan.path("appealPoints");
            if (appealPoints.isArray() && !appealPoints.isEmpty()) {
                sb.append("어필 포인트: ");
                for (int i = 0; i < appealPoints.size(); i++) {
                    if (i > 0) sb.append(" / ");
                    sb.append(appealPoints.get(i).asText());
                }
                sb.append("\n");
            }

            // 직무 분석
            JsonNode jobAnalysis = plan.path("jobAnalysis");
            if (!jobAnalysis.isMissingNode()) {
                String jobEssence = jobAnalysis.path("jobEssence").asText("");
                if (!jobEssence.isBlank()) {
                    sb.append("직무 본질: ").append(jobEssence).append("\n");
                }
                String hidden = jobAnalysis.path("hiddenExpectations").asText("");
                if (!hidden.isBlank()) {
                    sb.append("숨은 기대: ").append(hidden).append("\n");
                }
            }

            // 회피 사항
            JsonNode avoidances = plan.path("avoidances");
            if (avoidances.isArray() && !avoidances.isEmpty()) {
                sb.append("전체 회피사항: ");
                for (int i = 0; i < avoidances.size(); i++) {
                    if (i > 0) sb.append(" / ");
                    sb.append(avoidances.get(i).asText());
                }
                sb.append("\n");
            }

            // 이 문항의 전략
            JsonNode strategies = plan.path("questionStrategies");
            if (strategies.isArray()) {
                for (JsonNode qs : strategies) {
                    if (qs.path("questionIndex").asInt(0) == questionNumber) {
                        sb.append("\n[이 문항(").append(questionNumber).append("번)의 전략]\n");
                        String role = qs.path("roleInNarrative").asText("");
                        if (!role.isBlank()) sb.append("서사 내 역할: ").append(role).append("\n");
                        String angle = qs.path("primaryAngle").asText("");
                        if (!angle.isBlank()) sb.append("경험 서술 각도: ").append(angle).append("\n");
                        String keyMsg = qs.path("keyMessage").asText("");
                        if (!keyMsg.isBlank()) sb.append("핵심 메시지: ").append(keyMsg).append("\n");
                        String connection = qs.path("connectionToNext").asText("");
                        if (!connection.isBlank()) sb.append("다음 문항 연결: ").append(connection).append("\n");
                        break;
                    }
                }
            }

            sb.append("\n");
            return sb.toString();

        } catch (Exception e) {
            log.debug("masterPlan 파싱 실패: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 회사 분석 JSON에서 해당 문항의 작성 가이드를 추출합니다.
     */
    private String buildQuestionGuide(JobPosting jobPosting, EssayQuestion question) {
        String analysisJson = jobPosting.getCompanyAnalysis();
        if (analysisJson == null || analysisJson.isBlank()) return "";

        try {
            JsonNode analysis = objectMapper.readTree(analysisJson);
            JsonNode guides = analysis.path("questionGuides");
            if (!guides.isArray()) return "";

            for (JsonNode guide : guides) {
                if (guide.path("questionIndex").asInt(0) == question.number()) {
                    StringBuilder sb = new StringBuilder();

                    String strategy = guide.path("writingStrategy").asText("");
                    if (!strategy.isBlank()) {
                        sb.append("[이 문항의 작성 전략]\n").append(strategy).append("\n\n");
                    }

                    JsonNode mustInclude = guide.path("mustInclude");
                    if (mustInclude.isArray() && !mustInclude.isEmpty()) {
                        sb.append("[반드시 포함할 포인트]\n");
                        for (JsonNode item : mustInclude) {
                            sb.append("- ").append(item.asText()).append("\n");
                        }
                        sb.append("\n");
                    }

                    JsonNode avoid = guide.path("avoid");
                    if (avoid.isArray() && !avoid.isEmpty()) {
                        sb.append("[피해야 할 것]\n");
                        for (JsonNode item : avoid) {
                            sb.append("- ").append(item.asText()).append("\n");
                        }
                        sb.append("\n");
                    }

                    String exampleOpening = guide.path("exampleOpening").asText("");
                    if (!exampleOpening.isBlank()) {
                        sb.append("[추천 도입 문장 예시]\n").append(exampleOpening).append("\n");
                    }

                    return sb.toString();
                }
            }
        } catch (Exception e) {
            log.debug("문항별 가이드 추출 실패", e);
        }
        return "";
    }

    private void appendAnalysisField(StringBuilder sb, JsonNode analysis, String field, String label) {
        String value = analysis.path(field).asText("");
        if (!value.isBlank()) {
            sb.append("- ").append(label).append(": ").append(value).append("\n");
        }
    }

    private void appendAnalysisArray(StringBuilder sb, JsonNode analysis, String field, String label) {
        JsonNode arr = analysis.path(field);
        if (arr.isArray() && !arr.isEmpty()) {
            sb.append("- ").append(label).append(": ");
            StringBuilder items = new StringBuilder();
            for (JsonNode item : arr) {
                if (!items.isEmpty()) items.append(", ");
                if (item.isObject()) {
                    // 객체 배열 하위호환: name 필드가 있으면 name만 추출
                    String name = item.path("name").asText("");
                    items.append(name.isBlank() ? item.toString() : name);
                } else {
                    items.append(item.asText());
                }
            }
            sb.append(items).append("\n");
        }
    }

    private void appendAnalysisObjectArray(StringBuilder sb, JsonNode analysis, String field, String label,
                                            String nameKey, String descKey) {
        JsonNode arr = analysis.path(field);
        if (arr.isArray() && !arr.isEmpty()) {
            sb.append("- ").append(label).append(":\n");
            for (JsonNode item : arr) {
                if (item.isObject()) {
                    String name = item.path(nameKey).asText("");
                    String desc = item.path(descKey).asText("");
                    if (!name.isBlank()) {
                        sb.append("  · ").append(name);
                        if (!desc.isBlank()) sb.append(" — ").append(desc);
                        sb.append("\n");
                    }
                } else {
                    // 하위호환: 문자열 배열도 처리
                    sb.append("  · ").append(item.asText()).append("\n");
                }
            }
        }
    }

    private String classifyQuestionType(String questionText) {
        String q = questionText.toLowerCase();

        if (q.contains("포트폴리오") || q.contains("github") || q.contains("깃허브")
            || q.contains("블로그") || q.contains("url") || q.contains("링크")
            || q.contains("첨부") || q.contains("파일") || q.contains("노션")) {
            return "포트폴리오";
        }
        if (q.contains("지원동기") || q.contains("지원 동기")
            || (q.contains("왜") && q.contains("회사"))
            || (q.contains("선택") && q.contains("이유"))
            || q.contains("지원하게 된")) {
            return "지원동기";
        }
        if (q.contains("역량") || q.contains("강점") || q.contains("능력") || q.contains("직무")
            || q.contains("전문") || q.contains("기술") || q.contains("경쟁력")) {
            return "핵심역량";
        }
        if (q.contains("문제") || q.contains("해결") || q.contains("도전") || q.contains("어려움")
            || q.contains("극복") || q.contains("실패") || q.contains("위기")) {
            return "문제해결";
        }
        if (q.contains("협업") || q.contains("리더") || q.contains("팀") || q.contains("소통")
            || q.contains("갈등") || q.contains("설득") || q.contains("커뮤니케이션") || q.contains("조직")) {
            return "협업리더십";
        }
        if ((q.contains("입사") && q.contains("후")) || q.contains("포부") || q.contains("계획")
            || q.contains("비전") || q.contains("목표") || q.contains("각오")) {
            return "입사후포부";
        }
        if (q.contains("장단점") || q.contains("장점과 단점") || q.contains("장점") && q.contains("단점")
            || q.contains("약점") || q.contains("보완")) {
            return "장단점";
        }
        if (q.contains("성장") || q.contains("가치") || q.contains("인생") || q.contains("신념")
            || q.contains("좌우명") || q.contains("성격") || q.contains("본인 소개")) {
            return "성장과정";
        }
        return "일반";
    }

    private String getTypeGuide(String questionType) {
        return switch (questionType) {
            case "포트폴리오" -> """
                이 문항은 URL 또는 파일을 제출하는 칸입니다. 에세이를 쓰는 칸이 아닙니다.

                [필수 행동]
                - GitHub, 블로그, 노션, 개인 사이트 등 본인의 포트폴리오 URL 하나만 출력하세요.
                - 설명문을 길게 쓰지 마세요. URL만 출력하거나, 시스템이 텍스트를 허용하는 경우에도 1~2문장 이내로 간결하게.
                - 형식: "포트폴리오: https://..." 또는 URL만 단독 출력.

                [절대 금지]
                - 이 칸에 자기소개서 형식의 장문을 쓰면 형식 미준수로 감점됩니다.
                - 선택 문항이라도 형식 실수는 지원서 전체 완성도를 떨어뜨립니다.""";

            case "지원동기" -> """
                이 문항의 핵심: "왜 수많은 회사 중 이 회사여야 하는가?"

                [4단계 연결 필수 — 하나라도 빠지면 설득력 급감]
                1단계(산업/시장): 이 산업이 왜 중요한지, 시장 흐름에서 본인이 포착한 관점
                2단계(회사 특징): 그 산업 안에서 이 회사만의 차별점 (제품/기술/전략 고유명사 필수)
                3단계(직무 연결): 이 회사의 채용 포지션이 회사 전략에서 어떤 역할인지, 왜 이 직무가 매력적인지
                4단계(내 경험 적합성): 위 3단계와 직접 연결되는 본인의 경험·역량

                [기승전결 적용]
                기: 이 회사의 사업/기술/제품에서 발견한 구체적 매력 포인트로 시작. 일반적인 업계 이야기 금지.
                승: 그 매력 포인트와 연결되는 본인의 경험. "이 분야에서 이런 문제를 풀어본 나이기에 이 회사의 방향이 와닿는다."
                전: 경쟁사 대비 이 회사만의 차별점을 짚고, 왜 이 회사의 방향이 본인의 커리어와 맞닿는지 구체화.
                결: 입사 후 이 회사에서 구체적으로 풀고 싶은 문제. "OO 시스템의 XX를 YY 방법으로 개선하고 싶다" 수준.

                [필수 포함 요소]
                - 이 회사의 제품/서비스를 직접 써보거나 분석한 경험
                - 채용공고에서 발견한 구체적 기술 키워드와 본인 경험의 연결
                - 경쟁사가 아닌 이 회사여야 하는 이유 (구체적 차별점)
                - 이 회사에서 장기적으로 성장하고 싶다는 진정성 (추상적 다짐이 아닌 구체적 비전)

                [참고 예시 — 기(起) 단락]
                "XX사에서 결제 지연 이슈를 추적하던 중, YY사 기술블로그의 이벤트 소싱 아키텍처 글을 발견했습니다. 우리 팀이 3주간 헤맨 동시성 문제를, YY사는 CQRS 패턴 하나로 풀어내고 있었습니다. 그날 새벽, 해당 코드를 로컬에 재현하며 확신했습니다. 이 구조를 설계한 팀에서 일해야겠다고."
                (위 예시의 구조와 밀도를 참고하되, 실제 회사와 지원자 경험에 맞게 새로 쓰세요.)""";

            case "핵심역량" -> """
                이 문항의 핵심: "이 사람이 당장 일을 시킬 수 있는 사람인가?"

                [기승전결 적용]
                기: 이 직무에서 가장 중요한 역량이 무엇인지 한 문장으로 정의하며 시작.
                승: 그 역량을 증명하는 핵심 경험. 구체적 상황 → 문제 파악 → 해결 과정(기술 스택 포함) → 정량적 결과.
                전: 이 경험에서 얻은 역량이 이 회사의 현재 과제를 푸는 데 왜 적합한지 연결.
                결: 이 역량을 바탕으로 입사 후 첫 프로젝트에서 보여줄 구체적 성과.

                [필수 포함 요소]
                - 정량적 성과 (숫자, 비율, 기간, 규모)
                - 기술 스택·도구를 문장 안에 자연스럽게 녹인 서술
                - "나는 잘합니다"가 아닌, 경험 속 행동과 결과로 역량을 증명
                - 채용공고의 자격요건/우대사항 키워드와의 직접 연결

                [참고 예시 — 기(起) 단락]
                "XX시스템의 API 응답시간이 평균 2.3초를 넘기던 날, 저는 슬로우 쿼리 로그를 열었습니다. 원인은 N+1 문제가 아니었습니다. 인덱스가 걸려 있지만 카디널리티가 낮아 풀스캔과 다름없는 복합 조건 쿼리 세 개. 실행 계획을 뜯어 커버링 인덱스로 재설계하자, 응답시간이 0.4초로 떨어졌습니다."
                (위 예시의 구조와 밀도를 참고하되, 실제 회사와 지원자 경험에 맞게 새로 쓰세요.)""";

            case "문제해결" -> """
                이 문항의 핵심: "이 사람은 난관 앞에서 어떻게 사고하고 행동하는가?"

                [기승전결 적용]
                기: 문제의 심각성이나 긴박함을 한두 문장으로 생생하게 전달. 읽는 사람이 그 상황에 빠져들게.
                승: 문제의 근본 원인을 어떻게 분석했는지. 여러 대안 중 왜 특정 방법을 선택했는지 논리적으로.
                전: 실행 과정에서의 구체적 행동과 예상치 못한 변수 대응. 기술적 디테일 포함.
                결: 정량적 결과 + 이 경험에서 체득한 문제해결 프레임워크가 이 회사에서 어떻게 적용될지.

                [필수 포함 요소]
                - 문제 상황의 구체적 맥락 (숫자, 기간, 영향 범위)
                - 원인 분석 과정 (단순 "어려웠다"가 아닌, 왜 어려웠는지)
                - 대안 비교와 최종 선택의 논리
                - 정량적 결과와 교훈

                [참고 예시 — 기(起) 단락]
                "금요일 오후 6시, 배포 직후 결제 성공률이 94%에서 71%로 떨어졌습니다. 롤백하면 안전하지만, 이미 신규 스키마로 마이그레이션된 주문 3,200건이 꼬입니다. 저는 핫픽스를 선택했습니다. 로그를 추적하니 원인은 외부 PG 연동 타임아웃 값이 배포 스크립트에서 누락된 것. 22분 만에 패치를 올렸고, 성공률은 99.2%로 복구됐습니다."
                (위 예시의 구조와 밀도를 참고하되, 실제 회사와 지원자 경험에 맞게 새로 쓰세요.)""";

            case "협업리더십" -> """
                이 문항의 핵심: "이 사람과 함께 일하면 팀이 더 잘 돌아갈까?"

                [기승전결 적용]
                기: 팀 내 의견 충돌이나 갈등 상황을 구체적으로 묘사. 누가, 왜, 어떤 상황에서.
                승: 상대방의 입장을 이해하기 위해 한 구체적 행동. 일방적 설득이 아닌 경청과 조율.
                전: 합의점을 찾아 실행한 과정. 본인의 역할이 팀 전체에 미친 영향.
                결: 팀 차원의 성과(정량적) + 이 경험이 이 회사의 조직 문화에서 어떻게 발휘될지.

                [필수 포함 요소]
                - 갈등/충돌의 구체적 맥락 (추상적 "힘들었다" 금지)
                - 상대방 관점에 대한 이해 표현
                - 본인의 구체적 행동과 그 결과
                - 리더십은 직책이 아닌 행동으로 증명

                [참고 예시 — 기(起) 단락]
                "기획팀은 전면 UI 개편을, 개발팀은 백엔드 안정화를 주장했습니다. 스프린트 회의가 세 번째 평행선을 달리던 날, 저는 화이트보드 앞에 섰습니다. 양쪽 요구사항을 기능 단위로 분해하고, 백엔드 병목 3건을 먼저 해소하면 UI 개편 일정이 오히려 2주 단축된다는 의존성 그래프를 그렸습니다. 그날 합의가 나왔습니다."
                (위 예시의 구조와 밀도를 참고하되, 실제 회사와 지원자 경험에 맞게 새로 쓰세요.)""";

            case "입사후포부" -> """
                이 문항의 핵심: "이 사람이 정말로 이 회사를 연구했는가? 진짜 여기서 일하고 싶은가?"

                [기승전결 적용]
                기: 이 회사가 현재 직면한 과제나 추구하는 방향을 정확히 짚으며 시작.
                승: 그 과제를 풀기 위한 본인의 준비 상태. 관련 경험이나 역량을 간결하게.
                전: 구체적 3개월/6개월/1년 계획. "OO 팀에서 XX를 YY 방법으로" 수준의 구체성.
                결: 장기적으로 이 회사에서 이루고 싶은 것. 회사의 비전과 본인의 커리어 방향의 일치.

                [필수 포함 요소]
                - 추상적 다짐 절대 금지 ("열심히", "기여", "성장" → 탈락)
                - 이 회사의 현재 사업/기술 과제에 대한 구체적 언급
                - 첫 3개월 안에 할 일을 "팀명-과제명-방법론" 수준으로
                - 3년 후 이 회사에서의 구체적 모습

                [참고 예시 — 기(起) 단락]
                "XX사의 IR 자료에서 올해 동남아 결제 인프라 확장 계획을 읽었습니다. 저는 YY시스템에서 다국가 통화 정산 모듈을 설계한 경험이 있습니다. 첫 3개월간 현재 정산 파이프라인의 병목을 매핑하고, 6개월 안에 실시간 환율 반영 정산 시스템의 프로토타입을 제안하겠습니다."
                (위 예시의 구조와 밀도를 참고하되, 실제 회사와 지원자 경험에 맞게 새로 쓰세요.)""";

            case "장단점" -> """
                이 문항의 핵심: "자기 리스크를 인지하고 관리할 줄 아는 사람인가?"

                [기승전결 적용]
                기: 직무 맥락에서 의미 있는 장점을 한 문장으로 선언. 추상적 성격 나열 절대 금지.
                승: 장점이 발현된 구체적 에피소드. 상황→행동→결과(정량적). "저는 소통을 잘합니다"가 아닌, 실제 장면으로 증명.
                전: 단점을 솔직히 인정하되, 그 단점이 업무에서 어떤 문제를 일으켰는지 구체적 장면 서술. 그리고 그것을 어떻게 인지하고 관리하고 있는지 현재진행형 행동.
                결: 장점과 단점의 관리가 이 회사/직무에서 어떻게 작용할지 연결.

                [필수 포함 요소]
                - 장점은 "성격 특성"이 아닌 "직무 역량"으로 서술 (예: "꼼꼼함" → "QA 프로세스에서 결함 검출률 30% 향상")
                - 단점은 절대 장점으로 포장하지 말 것 (예: "일을 너무 열심히 한다" 같은 답변은 즉시 탈락)
                - 단점 관리의 구체적 행동 (인지→대응→현재 상태)
                - 형식적 "극복했습니다" 금지. 현재진행형으로 관리 중임을 보여줄 것

                [참고 예시 — 기(起) 단락]
                "저의 가장 큰 강점은 코드 리뷰에서 발휘됩니다. XX프로젝트에서 200건 이상의 리뷰를 수행하며, 배포 후 결함률을 12%에서 3%로 낮췄습니다. 반면, 코드 품질에 대한 높은 기준이 때로 PR 승인 지연으로 이어졌습니다. 이를 인지한 후, 리뷰 기준을 3단계로 나누고 중요도별 응답 시간을 정했습니다."
                (위 예시의 구조와 밀도를 참고하되, 실제 회사와 지원자 경험에 맞게 새로 쓰세요.)""";

            case "성장과정" -> """
                이 문항의 핵심: "이 사람은 어떤 가치관을 가진 사람이고, 왜 그런 사람이 되었는가?"

                [기승전결 적용]
                기: 인생의 전환점이 된 순간의 장면을 생생하게. 마치 영화의 오프닝처럼.
                승: 그 경험의 맥락과 과정. 어떤 선택을 했고, 왜 그 선택을 했는지.
                전: 그 경험이 현재의 가치관/직업관을 어떻게 형성했는지. 이전과 이후의 변화.
                결: 그 가치관이 이 회사에서 일하는 방식과 어떻게 연결되는지.

                [필수 포함 요소]
                - 인생 전환점의 구체적 장면 (날짜, 장소, 상황)
                - 추상적 미사여구가 아닌, 구체적 행동과 선택으로 가치관 증명
                - 핵심 신념을 한 문장으로 표현
                - 그 가치관이 이 회사의 문화/방향과 맞닿는 지점

                [참고 예시 — 기(起) 단락]
                "2019년 겨울, 아버지의 인쇄소가 문을 닫았습니다. 단골 고객이 하나둘 온라인으로 빠져나가는 걸 지켜보면서, 데이터를 읽을 줄 아는 사람이 곁에 있었다면 달랐을까 생각했습니다. 그때부터 저의 방향은 정해졌습니다. 데이터로 사람의 일을 살리는 엔지니어."
                (위 예시의 구조와 밀도를 참고하되, 실제 회사와 지원자 경험에 맞게 새로 쓰세요.)""";

            default -> """
                이 문항의 핵심 키워드를 파악하고, 다음 기승전결 구조를 적용하세요:

                기: 문항의 주제와 연결되는 강렬한 첫 문장. 이 회사의 구체적 특성 짚기.
                승: 관련 경험 서술. 상황 → 문제 파악 → 해결 → 결과.
                전: 이 경험이 이 회사에서 왜 중요한지 연결.
                결: 입사 후 구체적 액션 플랜.

                채용공고의 요구사항과 지원자의 경험을 자연스럽게 연결하세요.
                HR 평가 3축(직무적합성 40%, 조직적합성 35%, 성장가능성 25%) 중 문항에 맞는 축을 강조하세요.

                [참고 예시 — 기(起) 단락]
                "정확히 47일. XX시스템 안정화에 투입된 시간입니다. 매일 새벽 4시에 모니터링 대시보드를 열었고, 장애 알림 빈도가 하루 12건에서 0.8건으로 줄어드는 걸 지켜봤습니다. 안정화율 94%에서 99.2%. 그 숫자가 저에게 확신을 줬습니다."
                (위 예시의 구조와 밀도를 참고하되, 실제 회사와 지원자 경험에 맞게 새로 쓰세요.)""";
        };
    }

    /**
     * 경험 목록을 주력/보조로 구분하여 포맷합니다.
     * 첫 번째 경험 = 주력, 나머지 = 보조.
     */
    private String formatQuestionExperiences(UserExperience primary, List<UserExperience> secondary) {
        if (primary == null && (secondary == null || secondary.isEmpty())) {
            return "(등록된 경험 없음)";
        }

        StringBuilder sb = new StringBuilder();
        if (primary != null) {
            sb.append("[이 문항의 주력 경험 — 승(承) 단락에서 이 경험을 깊이 있게 서술하세요]\n");
            sb.append(formatExperience(primary));
        }

        if (secondary != null && !secondary.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("[보조 경험 — 필요 시 간접적으로 언급할 수 있지만, 주력 경험이 중심이어야 합니다]");
            for (int i = 0; i < secondary.size(); i++) {
                sb.append("\n").append(formatExperience(secondary.get(i)));
                if (i < secondary.size() - 1) sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String formatExperience(UserExperience e) {
        StringBuilder sb = new StringBuilder();
        sb.append("[%s] %s (%s)\n%s".formatted(
            e.getCategory(), e.getTitle(), e.getPeriod(), e.getDescription()));

        if (e.getSkills() != null && !e.getSkills().isBlank()) {
            sb.append("\n기술스택: ").append(e.getSkills());
        }
        return sb.toString();
    }

    /**
     * 직무 설명이 빈약할 때, 지원자 경험에서 기술스택을 추출하여 직무 정합성 지시문을 생성합니다.
     * 프롬프트 상단([최우선 규칙] 바로 뒤)에 삽입되어 AI가 올바른 직무로 작성하도록 강제합니다.
     */
    private String buildJobRoleDirective(JobPosting jobPosting, List<UserExperience> experiences) {
        if (experiences == null || experiences.isEmpty()) return "";

        Set<String> skills = new LinkedHashSet<>();
        for (UserExperience exp : experiences) {
            if (exp.getSkills() != null && !exp.getSkills().isBlank()) {
                for (String skill : exp.getSkills().split("[,/·]+")) {
                    String trimmed = skill.trim();
                    if (!trimmed.isBlank() && trimmed.length() >= 2) {
                        skills.add(trimmed);
                    }
                }
            }
        }
        if (skills.isEmpty()) return "";

        String skillList = skills.stream().limit(12).collect(Collectors.joining(", "));
        String inferredRole = inferRole(skills);

        String jd = jobPosting.getJobDescription();
        if (jd != null && jd.length() >= 100) {
            // JD가 충분한 경우: 경량 지시문 (사용자 프로필만 전달)
            return """

            [지원자 프로필]
            보유 기술: %s
            추정 직무 분야: %s
            위 기술스택과 직무 분야에 맞는 자소서를 작성하세요.
            """.formatted(skillList, inferredRole);
        }

        // JD가 빈약한 경우: 강한 지시문 (기존 유지)
        return """

            [직무 정합성 — 위반 시 즉시 탈락]
            채용공고의 직무 설명이 매우 제한적입니다.
            지원자 보유 기술: %s
            추정 직무 분야: %s
            반드시 위 기술스택과 직무 분야에 맞는 자소서를 작성하세요.
            지원자 경험과 무관한 직무(예: %s)로 작성하면 즉시 탈락입니다.
            """.formatted(skillList, inferredRole, getIrrelevantRoles(inferredRole));
    }

    private String inferRole(Set<String> skills) {
        String combined = String.join(" ", skills).toLowerCase();

        int backend = countMatches(combined, "spring", "jpa", "querydsl", "mybatis",
            "hibernate", "jdbc", "java", "kotlin", "서버", "backend", "백엔드",
            "mysql", "postgresql", "redis", "kafka", "rest", "api", "msa", "jdk");
        int frontend = countMatches(combined, "react", "vue", "angular", "javascript",
            "typescript", "프론트", "frontend", "css", "html", "next", "nuxt", "svelte");
        int data = countMatches(combined, "python", "데이터", "머신러닝", "ml",
            "tensorflow", "pytorch", "pandas", "spark", "airflow");

        if (backend >= frontend && backend >= data) return "백엔드 개발";
        if (frontend > backend && frontend >= data) return "프론트엔드 개발";
        if (data > backend && data > frontend) return "데이터/ML 엔지니어링";
        return "소프트웨어 개발";
    }

    private int countMatches(String text, String... keywords) {
        int count = 0;
        for (String kw : keywords) {
            if (text.contains(kw)) count++;
        }
        return count;
    }

    private String getIrrelevantRoles(String inferredRole) {
        return switch (inferredRole) {
            case "백엔드 개발" -> "프론트엔드, 디자인, 기획, 데이터분석";
            case "프론트엔드 개발" -> "백엔드, 인프라, 데이터분석, 기획";
            case "데이터/ML 엔지니어링" -> "프론트엔드, 디자인, 기획";
            default -> "지원자 기술스택과 무관한 분야";
        };
    }

    private String resolveTone(CompanyType companyType) {
        return switch (companyType) {
            case LARGE_CORP -> """
                안정감 있되 딱딱하지 않은 문체. 조직 내 체계적 프로세스와 협업 경험이 자연스럽게 드러나야 합니다.
                성과를 팀 차원에서 이야기하되 본인의 구체적 기여가 선명하게 보여야 합니다.
                이 대기업의 규모와 시스템을 이해하는 사람이라는 인상을 주세요.
                대기업 특유의 의사결정 구조, 부서 간 협업, 프로세스 준수에 대한 이해를 녹이세요.""";

            case FINANCE -> """
                정확하고 신뢰감 있는 문체. 데이터와 숫자를 근거로 이야기하세요.
                리스크 인식, 컴플라이언스, 꼼꼼한 검증 경험이 자연스럽게 드러나야 합니다.
                금융 업계의 규제 환경과 보안 민감성을 이해하는 사람이라는 인상을 주세요.
                안정성과 혁신의 균형을 아는 사람으로 보이세요.""";

            case STARTUP -> """
                솔직하고 에너지 있는 문체. 스스로 문제를 정의하고 해결한 경험을 중심으로 쓰세요.
                멀티 롤, 빠른 실행, 불확실한 환경에서의 판단력이 드러나야 합니다.
                "시키면 하는 사람"이 아닌 "스스로 찾아서 하는 사람"이라는 인상을 주세요.
                성장하는 조직에서 함께 성장할 준비가 된 사람으로 보이세요.""";

            case MID_IT, UNKNOWN -> """
                실무 중심의 담백한 문체. 기술 스택을 문장 안에 자연스럽게 녹이세요.
                어떤 문제를 어떻게 풀었는지가 핵심입니다. 불필요한 수식어를 빼세요.
                실무에 바로 투입 가능한 사람이라는 인상을 주세요.
                기술 깊이와 비즈니스 이해를 동시에 보여주세요.""";
        };
    }
}
