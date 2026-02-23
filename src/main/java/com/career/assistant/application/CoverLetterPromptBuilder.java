package com.career.assistant.application;

import com.career.assistant.domain.experience.UserExperience;
import com.career.assistant.domain.jobposting.CompanyType;
import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.infrastructure.crawling.EssayQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CoverLetterPromptBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String build(JobPosting jobPosting, List<UserExperience> experiences) {
        String experienceSummary = experiences.stream()
            .map(this::formatExperience)
            .collect(Collectors.joining("\n\n"));

        String tone = resolveTone(jobPosting.getCompanyType());
        String companyAnalysis = buildCompanyAnalysisGuide(jobPosting);

        return """
            아래 채용공고와 지원자 경험을 바탕으로, 이 회사에만 쓸 수 있는 자소서를 작성하세요.
            회사명을 다른 회사로 바꾸면 전혀 맞지 않는, 오직 이 회사만을 위한 글이어야 합니다.

            [최우선 규칙]
            순수 텍스트만 출력. 마크다운, 소제목, 번호, 불릿 전부 금지.
            단락 사이 빈 줄 하나로 구분. 자소서 본문만 출력.
            800자 이상 1000자 이하.

            [기업 분석 — 반드시 자소서에 반영할 것]
            %s

            [기승전결 4단락 구성 — 제목 없이]

            기(起) 단락 (약 150자) — 강렬한 도입.
            "지원하게 된 계기는~" 절대 금지. 이 회사의 사업/기술/제품에서 발견한 구체적 포인트로 시작하세요.
            채용공고에 나온 기술 키워드나 사업 내용을 정확히 짚으면서, "이 문제를 나도 풀어봤다" 또는 "이 방향에 내 경험이 정확히 맞닿는다"는 느낌을 주세요.
            첫 문장이 면접관의 눈을 잡아야 합니다.

            승(承) 단락 (약 350자) — 핵심 경험으로 역량 증명.
            가장 관련성 높은 경험 1개를 깊이 있게 서술하세요. 나열 금지.
            구체적 상황 → 내가 파악한 문제의 본질 → 내가 선택한 해결 방법과 그 이유 → 실행 과정(기술 스택, 도구) → 정량적 결과.
            프로젝트명은 문장 안에 자연스럽게 녹이세요.

            전(轉) 단락 (약 300자) — 이 회사와의 연결.
            내 경험이 왜 이 회사에서 중요한지, 이 회사가 지금 직면한 과제나 추구하는 방향과 내 역량이 어떻게 맞물리는지를 보여주세요.
            회사의 경쟁 우위, 핵심 사업, 기술 방향을 구체적으로 언급하면서 연결하세요.
            "이 회사가 아니면 안 되는 이유"가 여기서 드러나야 합니다.

            결(結) 단락 (약 200자) — 입사 후 구체적 액션.
            "열심히 하겠습니다", "기여하겠습니다" 같은 추상적 다짐은 0점입니다.
            첫 3개월 안에 구체적으로 무엇을 하겠는지, 어떤 기술적 문제를 풀겠는지 쓰세요.
            이 회사의 현재 과제와 본인의 역량이 만나는 지점을 보여주세요.

            [충성도 표현 — 자연스럽게 녹이세요]
            - 이 회사의 제품/서비스/기술을 직접 경험한 이야기를 포함하세요.
            - 경쟁사가 아닌 이 회사를 선택한 구체적 이유가 드러나야 합니다.
            - 회사의 최근 행보나 기술 방향에 대한 구체적 언급을 포함하세요.

            [HR 평가 포인트]
            - 직무적합성: 이 직무를 수행할 구체적 역량과 경험이 보이는가?
            - 조직적합성: 이 회사의 문화와 가치관에 맞는 사람인가?
            - 성장가능성: 장기적으로 이 회사에서 성장하고 싶은 진정성이 있는가?

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

            [AI 판별 회피 — 필수 적용 기법]
            1. 문장 리듬 변주: 짧은(15자)과 긴(35~45자) 2:1 교차
            2. 감각적 디테일: 오감 기반 표현 최소 2회 ("새벽 4시 서버실 냉기")
            3. 구어체 전환어: "솔직히", "돌이켜보면", "그날은" 중 2~3개
            4. 불완전 문장: 수사적 질문/문장 파편 1~2회 ("47일. 그게 필요한 시간이었습니다.")
            5. 감정 동사: "짜릿했습니다", "억울했습니다", "오기가 생겼습니다" 최소 2회
            6. 비정형 숫자: "정확히 47일", "2.3배", "새벽 4시 17분"

            [톤]
            %s

            [채용공고]
            회사: %s
            직무설명: %s
            자격요건: %s

            [지원자 경험]
            %s""".formatted(
                companyAnalysis,
                tone,
                jobPosting.getCompanyName(),
                jobPosting.getJobDescription(),
                jobPosting.getRequirements(),
                experienceSummary
            );
    }

    public String buildForQuestion(JobPosting jobPosting, List<UserExperience> experiences,
                                    EssayQuestion question) {
        String experienceSummary = experiences.stream()
            .map(this::formatExperience)
            .collect(Collectors.joining("\n\n"));

        String tone = resolveTone(jobPosting.getCompanyType());
        String questionType = classifyQuestionType(question.questionText());
        String typeGuide = getTypeGuide(questionType);
        String companyAnalysis = buildCompanyAnalysisGuide(jobPosting);
        String questionGuide = buildQuestionGuide(jobPosting, question);
        int charLimit = question.charLimit() > 0 ? question.charLimit() : 1000;

        return """
            아래 채용공고의 자소서 문항에 대한 답변을 작성하세요.
            이 회사에만 쓸 수 있는 답변이어야 합니다. 회사명을 바꾸면 맞지 않는 수준으로 쓰세요.

            [최우선 규칙]
            순수 텍스트만 출력. 마크다운, 소제목, 번호, 불릿 전부 금지.
            단락 사이 빈 줄 하나로 구분. 자소서 본문만 출력.
            반드시 %d자 이내로 작성하세요. (공백 포함, 초과 절대 금지)

            [문항 %d번]
            %s

            [기업 분석 — 반드시 답변에 반영할 것]
            %s

            [이 문항 전용 작성 가이드]
            %s

            [문항 유형: %s]
            %s

            [기승전결 서사 구조 — 이 문항에 맞게 적용]
            기(起): 문항의 핵심 주제와 연결되는 강렬한 첫 문장. 이 회사의 구체적 특성을 짚으며 시작하세요.
            승(承): 관련 경험 서술. 구체적 상황 → 문제 파악 → 해결 방법 선택 이유 → 실행(기술/행동) → 정량적 결과.
            전(轉): 왜 이 경험이 이 회사에서 중요한지. 회사의 현재 과제·방향과 내 역량의 접점.
            결(結): 이 회사에서 구체적으로 무엇을 하겠는지. 첫 3개월 액션 플랜.
            (글자수에 따라 단락 비중을 조절하되, 기승전결 흐름은 반드시 유지)

            [충성도 표현 — 자연스럽게 녹이세요]
            - 이 회사의 제품/서비스/기술을 직접 경험한 이야기를 포함하세요.
            - 경쟁사가 아닌 이 회사를 선택한 구체적 이유가 드러나야 합니다.
            - 회사의 최근 행보나 기술 방향에 대한 구체적 언급을 포함하세요.

            [HR 평가 포인트 — 이 문항에서 보여줄 것]
            - 직무적합성: 이 직무를 수행할 구체적 역량과 경험이 보이는가?
            - 조직적합성: 이 회사의 문화와 가치관에 맞는 사람인가?
            - 성장가능성: 장기적으로 이 회사에서 성장하고 싶은 진정성이 있는가?

            [키워드 전략]
            - 행동동사 중심: "설계했습니다", "주도했습니다", "구축했습니다"
            - 수동적 표현 금지: "~하게 되었습니다", "~할 수 있었습니다", "~에 참여했습니다"
            - 채용공고에 나온 기술/역량 키워드를 답변에 자연스럽게 포함하세요.
            - 숫자와 고유명사를 반드시 포함하세요.

            [금지 표현]
            "의미 있다고 생각합니다" / "깊이 있는 이해" / "~에 기여하겠습니다"
            "~를 극대화하겠습니다" / "이러한 경험은 ~에 활용될 수 있습니다"
            "~의 중요성을 깨달았습니다" / "더 나아가" / "무엇보다"
            "소중한 경험" / "값진 교훈" / "뜻깊은 시간"
            같은 어미(~했습니다) 3회 연속 사용

            [AI 판별 회피 — 필수 적용 기법]
            1. 문장 리듬 변주: 짧은(15자)과 긴(35~45자) 2:1 교차
            2. 감각적 디테일: 오감 기반 표현 최소 2회 ("새벽 4시 서버실 냉기")
            3. 구어체 전환어: "솔직히", "돌이켜보면", "그날은" 중 2~3개
            4. 불완전 문장: 수사적 질문/문장 파편 1~2회 ("47일. 그게 필요한 시간이었습니다.")
            5. 감정 동사: "짜릿했습니다", "억울했습니다", "오기가 생겼습니다" 최소 2회
            6. 비정형 숫자: "정확히 47일", "2.3배", "새벽 4시 17분"

            [톤]
            %s

            [채용공고]
            회사: %s
            직무설명: %s
            자격요건: %s

            [지원자 경험]
            %s""".formatted(
                charLimit,
                question.number(),
                question.questionText(),
                companyAnalysis,
                questionGuide.isBlank() ? "(분석 데이터 없음 — 채용공고에서 직접 파악하세요)" : questionGuide,
                questionType,
                typeGuide,
                tone,
                jobPosting.getCompanyName(),
                jobPosting.getJobDescription(),
                jobPosting.getRequirements(),
                experienceSummary
            );
    }

    public String buildImprovementPrompt(JobPosting jobPosting, List<UserExperience> experiences,
                                           String question, String previousDraft,
                                           String reviewFeedbackJson, int iterationNum) {
        String experienceSummary = experiences.stream()
            .map(this::formatExperience)
            .collect(Collectors.joining("\n\n"));

        String tone = resolveTone(jobPosting.getCompanyType());
        String companyAnalysis = buildCompanyAnalysisGuide(jobPosting);

        return """
            당신은 채용팀장의 피드백을 받고 자소서를 개선하는 전문가입니다.
            아래의 검토 피드백을 꼼꼼히 반영하여 자소서를 개선하세요.

            [최우선 규칙]
            순수 텍스트만 출력. 마크다운, 소제목, 번호, 불릿 전부 금지.
            단락 사이 빈 줄 하나로 구분. 자소서 본문만 출력.

            [개선 원칙 — %d차 개선]
            1. 잘 쓴 부분은 반드시 유지하거나 더 강화하세요.
            2. violations에 지적된 문장은 반드시 수정하세요.
            3. improvements에 제시된 개선 방향과 예시를 적극 참고하세요.
            4. AI탐지 위험도가 높다면: 추상적 표현을 구체적으로 바꾸고, 어미 반복을 깨고, 감정과 생생한 디테일을 추가하세요.
            5. 구체성 점수가 낮다면: 숫자, 프로젝트명, KPI, 정량적 성과를 반드시 추가하세요.
            6. 조직적합도가 낮다면: 이 회사만의 특성, 문화, 기술 방향을 더 구체적으로 언급하세요.
            7. 키워드 활용이 낮다면: 채용공고의 핵심 키워드를 자연스럽게 녹이세요.

            [검토 피드백 (채용팀장)]
            %s

            [이전 초안]
            %s

            [자소서 문항]
            %s

            [기업 분석]
            %s

            [톤]
            %s

            [채용공고]
            회사: %s
            직무설명: %s
            자격요건: %s

            [지원자 경험]
            %s

            위 피드백을 모두 반영하여 개선된 자소서를 작성하세요. 자소서 본문만 출력하세요.""".formatted(
                iterationNum,
                reviewFeedbackJson,
                previousDraft,
                question != null ? question : "(단일 자소서)",
                companyAnalysis,
                tone,
                jobPosting.getCompanyName(),
                jobPosting.getJobDescription() != null ? jobPosting.getJobDescription() : "",
                jobPosting.getRequirements() != null ? jobPosting.getRequirements() : "",
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
                appendAnalysisArray(guide, analysis, "coreProducts", "핵심 제품/서비스");
                appendAnalysisField(guide, analysis, "competitiveAdvantage", "경쟁 우위");
                appendAnalysisArray(guide, analysis, "competitors", "주요 경쟁사");
                appendAnalysisField(guide, analysis, "hiringReason", "채용 배경 (이 포지션을 뽑는 이유)");
                appendAnalysisField(guide, analysis, "idealCandidate", "이상적 지원자 프로필");
                appendAnalysisField(guide, analysis, "companyValues", "핵심 가치/문화");
                appendAnalysisField(guide, analysis, "techStack", "기술 스택/방향");
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
                items.append(item.asText());
            }
            sb.append(items).append("\n");
        }
    }

    private String classifyQuestionType(String questionText) {
        String q = questionText.toLowerCase();

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
        if (q.contains("성장") || q.contains("가치") || q.contains("인생") || q.contains("신념")
            || q.contains("좌우명") || q.contains("성격") || q.contains("장단점") || q.contains("본인 소개")) {
            return "성장과정";
        }
        return "일반";
    }

    private String getTypeGuide(String questionType) {
        return switch (questionType) {
            case "지원동기" -> """
                이 문항의 핵심: "왜 수많은 회사 중 이 회사여야 하는가?"

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

    private String formatExperience(UserExperience e) {
        StringBuilder sb = new StringBuilder();
        sb.append("[%s] %s (%s)\n%s".formatted(
            e.getCategory(), e.getTitle(), e.getPeriod(), e.getDescription()));

        if (e.getSkills() != null && !e.getSkills().isBlank()) {
            sb.append("\n기술스택: ").append(e.getSkills());
        }
        return sb.toString();
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
