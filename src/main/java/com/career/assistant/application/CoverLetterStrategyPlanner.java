package com.career.assistant.application;

import com.career.assistant.domain.experience.UserExperience;
import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.infrastructure.ai.AiPort;
import com.career.assistant.infrastructure.crawling.EssayQuestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CoverLetterStrategyPlanner {

    private static final String SYSTEM_PROMPT = """
        당신은 자소서 전략 기획 전문가입니다.
        채용공고, 회사 분석, 자소서 문항, 지원자 경험을 종합하여
        전체 자소서의 통합 전략을 JSON으로 수립합니다.
        반드시 순수 JSON만 출력하세요. 마크다운 코드블록(```) 없이.""";

    private final AiPort claudeHaiku;

    public CoverLetterStrategyPlanner(@Qualifier("claudeHaiku") AiPort claudeHaiku) {
        this.claudeHaiku = claudeHaiku;
    }

    public String planStrategy(JobPosting jobPosting, List<EssayQuestion> questions,
                                List<UserExperience> allExperiences) {
        if (questions == null || questions.size() < 2) {
            log.debug("[전략] 문항 2개 미만 — 전략 수립 생략");
            return null;
        }

        try {
            String userPrompt = buildStrategyPrompt(jobPosting, questions, allExperiences);
            String response = claudeHaiku.generate(SYSTEM_PROMPT, userPrompt);

            String json = extractJson(response);
            if (json != null) {
                log.info("[전략] 통합 전략 수립 완료 — 문항 {}개, 경험 {}건",
                    questions.size(), allExperiences.size());
                return json;
            }

            log.warn("[전략] 전략 JSON 추출 실패");
            return null;

        } catch (Exception e) {
            log.warn("[전략] 전략 수립 실패 — 개별 생성 방식으로 진행: {}", e.getMessage());
            return null;
        }
    }

    private String buildStrategyPrompt(JobPosting jobPosting, List<EssayQuestion> questions,
                                        List<UserExperience> experiences) {
        String questionsText = questions.stream()
            .map(q -> "문항 %d: %s (글자수 제한: %d자)".formatted(q.number(), q.questionText(), q.charLimit()))
            .collect(Collectors.joining("\n"));

        String experiencesText = experiences.stream()
            .map(e -> "[ID:%d] [%s] %s (%s)\n%s\n기술: %s".formatted(
                e.getId(), e.getCategory(), e.getTitle(), e.getPeriod(),
                e.getDescription() != null ? e.getDescription().substring(0, Math.min(200, e.getDescription().length())) : "",
                e.getSkills() != null ? e.getSkills() : ""))
            .collect(Collectors.joining("\n\n"));

        String companyAnalysis = jobPosting.getCompanyAnalysis() != null
            ? jobPosting.getCompanyAnalysis() : "(회사 분석 데이터 없음)";

        return """
            아래 정보를 종합하여 전체 자소서의 통합 전략을 수립하세요.

            [채용공고]
            회사: %s
            직무설명: %s
            자격요건: %s

            [회사 분석]
            %s

            [자소서 문항]
            %s

            [지원자 경험 목록]
            %s

            [평가 기준 — 8개 항목 모두 충족해야 함]
            1. 입사지원분야 경쟁력: 자격요건과 경험을 직접 연결
            2. 회사 분석: 기업분석 고유명사 2개 이상 포함
            3. 진부한 표현 없음: "기여하겠습니다", "성장하겠습니다" 등 금지
            4. 구체적 경험: 숫자, 프로젝트명, KPI 필수
            5. 필요항목 빠짐없이: 문항 하위 질문 모두 답변
            6. 간결하고 명료: 불필요한 수식어 삭제
            7. 맞춤법/띄어쓰기: 정확하게
            8. 열정: 이 회사가 아니면 안 되는 절실함

            다음 JSON 구조로 정확히 응답하세요:
            {
              "jobAnalysis": {
                "jobEssence": "이 직무의 핵심 역할 (2~3문장). 채용공고 텍스트를 그대로 옮기지 말고, 직무의 본질을 파악하세요.",
                "requiredCompetencies": ["핵심 역량 3~5가지"],
                "hiddenExpectations": "채용공고에 명시되지 않았지만 실제로 요구하는 것"
              },
              "appealPoints": ["지원자의 어필 포인트 3개 — 경험 목록에서 추출"],
              "narrativeTheme": "전체 자소서를 관통하는 서사 테마 (한 문장)",
              "questionStrategies": [
                {
                  "questionIndex": 1,
                  "roleInNarrative": "전체 서사에서 이 문항의 역할",
                  "primaryExperienceId": 1,
                  "primaryAngle": "이 경험을 서술할 각도/관점",
                  "keyMessage": "이 문항의 핵심 메시지 (한 문장)",
                  "connectionToNext": "다음 문항과의 서사적 연결고리"
                }
              ],
              "avoidances": ["전체 자소서에서 반드시 피할 것 3~5개"]
            }

            [전략 수립 원칙]
            - 같은 경험을 여러 문항에서 주력으로 사용하지 마세요. 경험을 분산 배치하세요.
            - 문항 간 서사가 유기적으로 연결되어야 합니다. 한 사람의 일관된 이야기여야 합니다.
            - questionStrategies는 위 문항 수(%d개)만큼 생성하세요.
            - primaryExperienceId는 [지원자 경험 목록]의 ID를 사용하세요.
            - narrativeTheme은 "성장하는 개발자" 같은 진부한 표현 금지. 이 지원자만의 고유한 테마를 찾으세요.
            """.formatted(
                jobPosting.getCompanyName() != null ? jobPosting.getCompanyName() : "미상",
                jobPosting.getJobDescription() != null ? jobPosting.getJobDescription() : "(정보 없음)",
                jobPosting.getRequirements() != null ? jobPosting.getRequirements() : "(정보 없음)",
                companyAnalysis,
                questionsText,
                experiencesText,
                questions.size()
            );
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
}
