package com.career.assistant.application.interview;

import com.career.assistant.common.AiResponseParser;
import com.career.assistant.domain.experience.UserExperience;
import com.career.assistant.domain.experience.UserExperienceRepository;
import com.career.assistant.domain.jobposting.CompanyType;
import com.career.assistant.domain.jobposting.JobPosting;
import com.career.assistant.infrastructure.ai.AiPort;
import com.career.assistant.infrastructure.ai.AiRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InterviewPrepAnalyzer {

    private static final String SYSTEM_PROMPT = """
        당신은 면접관 경력 15년의 면접 코칭 전문가입니다.
        채용공고, 회사 분석 결과, 지원자 경험을 바탕으로 면접 예상 질문과 답변 가이드를 생성합니다.

        [질문 생성 기준]
        - 인성 질문: 회사의 가치관, 조직 문화, 채용 배경 기반
        - 직무/기술 질문: 자격요건, 기술스택, 직무설명 기반
        - 경험 기반 질문: 지원자의 경험과 직무 요구사항의 교집합

        [답변 가이드 원칙]
        - 지원자의 실제 경험을 참조하여 구체적인 답변 방향 제시
        - STAR 기법(상황-과제-행동-결과) 구조 권장
        - 해당 회사에 특화된 답변 포인트 포함

        [코딩테스트/과제]
        - 채용공고에 코딩테스트, 과제전형, 기술면접 코딩 등의 언급이 있으면 hasCodingTest: true
        - 언급이 없으면 hasCodingTest: false, 나머지 필드는 빈 값

        [출력 규칙]
        - 카테고리별 최대 3개 질문
        - 반드시 순수 JSON만 출력하세요. 마크다운 코드블록(```) 없이 JSON만 출력하세요.
        - 모든 내용은 한국어로 작성""";

    private final AiRouter aiRouter;
    private final UserExperienceRepository userExperienceRepository;
    private final ObjectMapper objectMapper;

    public InterviewPrepAnalyzer(AiRouter aiRouter,
                                  UserExperienceRepository userExperienceRepository,
                                  ObjectMapper objectMapper) {
        this.aiRouter = aiRouter;
        this.userExperienceRepository = userExperienceRepository;
        this.objectMapper = objectMapper;
    }

    public InterviewPrepResult analyze(JobPosting jobPosting) {
        CompanyType companyType = jobPosting.getCompanyType() != null
            ? jobPosting.getCompanyType() : CompanyType.UNKNOWN;
        AiPort ai = aiRouter.route(companyType);
        List<UserExperience> experiences = userExperienceRepository.findAll();

        String userPrompt = buildUserPrompt(jobPosting, experiences);
        log.info("[면접준비] AI 분석 요청 — {} (모델: {})", jobPosting.getCompanyName(), ai.getModelName());

        try {
            String response = ai.generate(SYSTEM_PROMPT, userPrompt);
            log.info("[면접준비] AI 응답 수신 — {}", jobPosting.getCompanyName());
            return parseResponse(response);
        } catch (Exception e) {
            log.error("[면접준비] AI 분석 실패 — {}. 폴백 결과 반환", jobPosting.getCompanyName(), e);
            return InterviewPrepResult.fallback();
        }
    }

    private String buildUserPrompt(JobPosting jobPosting, List<UserExperience> experiences) {
        String experienceText = experiences.stream()
            .map(this::formatExperience)
            .collect(Collectors.joining("\n\n"));

        return """
            [회사 정보]
            회사명: %s
            회사 유형: %s
            직무 설명: %s
            자격 요건: %s

            [회사 심층 분석]
            %s

            [지원자 경험]
            %s

            다음 JSON 구조로 정확히 응답하세요:
            {
              "behavioralQuestions": [
                {"question": "질문", "intent": "출제 의도", "answerGuide": "이 지원자의 경험을 참고한 답변 가이드"}
              ],
              "technicalQuestions": [
                {"question": "질문", "intent": "출제 의도", "answerGuide": "답변 가이드"}
              ],
              "experienceQuestions": [
                {"question": "질문", "intent": "출제 의도", "answerGuide": "답변 가이드"}
              ],
              "codingTestPrep": {
                "hasCodingTest": true/false,
                "testFormat": "예상 시험 형식",
                "keyTopics": ["핵심 주제1", "핵심 주제2"],
                "practiceProblems": [
                  {"title": "문제 제목", "difficulty": "난이도", "topic": "주제", "description": "설명"}
                ]
              }
            }""".formatted(
                jobPosting.getCompanyName() != null ? jobPosting.getCompanyName() : "미상",
                jobPosting.getCompanyType() != null ? jobPosting.getCompanyType().name() : "UNKNOWN",
                jobPosting.getJobDescription() != null ? jobPosting.getJobDescription() : "(정보 없음)",
                jobPosting.getRequirements() != null ? jobPosting.getRequirements() : "(정보 없음)",
                jobPosting.getCompanyAnalysis() != null ? jobPosting.getCompanyAnalysis() : "(분석 데이터 없음)",
                experienceText.isBlank() ? "(등록된 경험 없음)" : experienceText
            );
    }

    private InterviewPrepResult parseResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new RuntimeException("면접 준비 분석 응답이 비어있습니다.");
        }

        String json = AiResponseParser.extractJson(response);
        if (json == null) {
            String preview = response.substring(0, Math.min(300, response.length()));
            log.warn("[면접준비] JSON 추출 실패. 응답 미리보기: {}", preview);
            throw new RuntimeException("면접 준비 분석 응답에서 유효한 JSON을 추출할 수 없습니다.");
        }

        try {
            return objectMapper.readValue(json, InterviewPrepResult.class);
        } catch (Exception e) {
            String preview = json.substring(0, Math.min(300, json.length()));
            log.warn("[면접준비] JSON 파싱 실패: {}. 미리보기: {}", e.getMessage(), preview);
            throw new RuntimeException("면접 준비 분석 응답 파싱 실패.", e);
        }
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
}
