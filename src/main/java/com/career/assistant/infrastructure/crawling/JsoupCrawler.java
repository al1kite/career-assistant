package com.career.assistant.infrastructure.crawling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class JsoupCrawler {

    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";

    private static final Pattern ESSAY_QUESTION_PATTERN =
        Pattern.compile("(\\d+)\\.\\s*(.+?)\\s*[\\(（](\\d+)자[\\)）]");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${jasoseol.user-token:}")
    private String jasoseolUserToken;

    public CrawledJobInfo crawl(String url) {
        try {
            Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10_000)
                .get();

            if (url.contains("wanted.co.kr")) {
                return crawlWanted(doc);
            }

            if (url.contains("jasoseol.com")) {
                return crawlJasoseol(doc);
            }

            return CrawledJobInfo.of(
                extractCompanyName(doc, url),
                extractJobDescription(doc),
                extractRequirements(doc)
            );
        } catch (CrawlingException e) {
            throw e;
        } catch (IOException e) {
            log.error("크롤링 실패: {}", url, e);
            throw new CrawlingException("공고 페이지를 읽어올 수 없습니다: " + url);
        }
    }

    private CrawledJobInfo crawlWanted(Document doc) {
        Element scriptTag = doc.selectFirst("script#__NEXT_DATA__");
        if (scriptTag == null) {
            log.warn("원티드 __NEXT_DATA__ 스크립트 태그를 찾을 수 없습니다. 폴백 파싱 시도.");
            return CrawledJobInfo.of(
                doc.title(),
                extractJobDescription(doc),
                extractRequirements(doc)
            );
        }

        try {
            JsonNode root = objectMapper.readTree(scriptTag.data());
            JsonNode jobDetail = findJobDetail(root);

            if (jobDetail == null) {
                log.warn("원티드 JSON에서 채용 상세 정보를 찾을 수 없습니다. 폴백 파싱 시도.");
                return CrawledJobInfo.of(
                    doc.title(),
                    extractJobDescription(doc),
                    extractRequirements(doc)
                );
            }

            String companyName = extractTextFromJson(jobDetail, "company", "name");
            if (companyName.isBlank()) {
                companyName = extractTextFromJson(jobDetail, "company_name");
            }

            String jobDescription = extractTextFromJson(jobDetail, "position");
            if (jobDescription.isBlank()) {
                jobDescription = extractTextFromJson(jobDetail, "detail", "main_tasks");
            }

            String requirements = extractTextFromJson(jobDetail, "detail", "requirements");
            if (requirements.isBlank()) {
                requirements = extractTextFromJson(jobDetail, "detail", "intro");
            }

            String deadline = extractTextFromJson(jobDetail, "due_time");
            if (deadline.isBlank()) {
                deadline = extractTextFromJson(jobDetail, "end_at");
            }

            boolean active = isJobActive(jobDetail, deadline);

            if (!active) {
                throw new CrawlingException("마감된 공고입니다. 마감일: " + deadline);
            }

            // 상세 설명이 부족하면 여러 필드를 결합
            if (jobDescription.length() < 50) {
                StringBuilder sb = new StringBuilder();
                appendIfPresent(sb, jobDetail, "detail", "main_tasks", "주요업무");
                appendIfPresent(sb, jobDetail, "detail", "intro", "소개");
                appendIfPresent(sb, jobDetail, "detail", "preferred_points", "우대사항");
                if (!sb.isEmpty()) {
                    jobDescription = sb.toString();
                }
            }

            log.info("원티드 크롤링 성공 - 회사: {}, 마감: {}", companyName, deadline);

            // 원티드는 자소서 문항이 없으므로 빈 리스트
            return CrawledJobInfo.of(companyName, jobDescription, requirements, deadline, active, List.of());

        } catch (CrawlingException e) {
            throw e;
        } catch (Exception e) {
            log.error("원티드 __NEXT_DATA__ JSON 파싱 실패", e);
            return CrawledJobInfo.of(
                doc.title(),
                extractJobDescription(doc),
                extractRequirements(doc)
            );
        }
    }

    private JsonNode findJobDetail(JsonNode root) {
        // props.pageProps.job 경로 시도
        JsonNode node = root.path("props").path("pageProps").path("job");
        if (!node.isMissingNode() && node.isObject()) {
            return node;
        }
        // props.pageProps.jobDetail 경로 시도
        node = root.path("props").path("pageProps").path("jobDetail");
        if (!node.isMissingNode() && node.isObject()) {
            return node;
        }
        // props.pageProps.dehydratedState 경로 (React Query 캐시) 시도
        JsonNode queries = root.path("props").path("pageProps")
            .path("dehydratedState").path("queries");
        if (queries.isArray()) {
            for (JsonNode query : queries) {
                JsonNode state = query.path("state").path("data");
                if (state.has("company") || state.has("company_name") || state.has("position")) {
                    return state;
                }
            }
        }
        return null;
    }

    private boolean isJobActive(JsonNode jobDetail, String deadline) {
        // status 필드 확인
        String status = extractTextFromJson(jobDetail, "status");
        if (!status.isBlank()) {
            return "active".equalsIgnoreCase(status) || "open".equalsIgnoreCase(status);
        }

        // is_closed 필드 확인
        JsonNode isClosed = jobDetail.path("is_closed");
        if (!isClosed.isMissingNode()) {
            return !isClosed.asBoolean(false);
        }

        // 마감일 기반 판단
        if (!deadline.isBlank()) {
            try {
                java.time.LocalDate dueDate = java.time.LocalDate.parse(deadline.substring(0, 10));
                return !dueDate.isBefore(java.time.LocalDate.now());
            } catch (Exception e) {
                log.debug("마감일 파싱 불가: {}", deadline);
            }
        }

        // 판단 불가 → active로 간주
        return true;
    }

    private String extractTextFromJson(JsonNode node, String... fieldPath) {
        JsonNode current = node;
        for (String field : fieldPath) {
            if (current == null || current.isMissingNode()) {
                return "";
            }
            current = current.path(field);
        }
        return current.isMissingNode() ? "" : current.asText("");
    }

    private void appendIfPresent(StringBuilder sb, JsonNode jobDetail,
                                 String section, String field, String label) {
        String value = extractTextFromJson(jobDetail, section, field);
        if (!value.isBlank()) {
            sb.append("[").append(label).append("]\n").append(value).append("\n\n");
        }
    }

    private CrawledJobInfo crawlJasoseol(Document doc) {
        String companyName = "";
        String jobDescription = "";
        String requirements = "";
        String deadline = "";
        List<EssayQuestion> essayQuestions = new ArrayList<>();

        // 1순위: JSON-LD (schema.org/JobPosting) 파싱
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode ld = objectMapper.readTree(script.data());
                String ldType = ld.path("@type").asText("");
                log.debug("자소설닷컴 JSON-LD @type={}", ldType);

                // JobPosting 스키마에서만 추출
                if ("JobPosting".equals(ldType)) {
                    if (companyName.isBlank()) {
                        companyName = ld.path("hiringOrganization").path("name").asText("");
                    }
                    if (jobDescription.isBlank()) {
                        jobDescription = ld.path("description").asText("");
                    }
                    // validThrough만 마감일로 사용. datePosted는 게시일이므로 사용 안 함
                    JsonNode validThroughNode = ld.path("validThrough");
                    if (!validThroughNode.isMissingNode() && !validThroughNode.isNull()) {
                        String validThrough = validThroughNode.asText("");
                        if (!validThrough.isBlank()) {
                            deadline = validThrough;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("자소설닷컴 JSON-LD 파싱 스킵", e);
            }
        }

        // 2순위: og:title에서 회사명 추출 ("SK이터닉스 채용공고 - ..." 형태)
        if (companyName.isBlank()) {
            String ogTitle = doc.select("meta[property=og:title]").attr("content");
            if (ogTitle.contains("채용공고")) {
                companyName = ogTitle.split("채용공고")[0].trim();
            }
        }

        // 3순위: __NEXT_DATA__에서 시도 (Next.js SSG)
        Element nextData = doc.selectFirst("script#__NEXT_DATA__");
        if (nextData != null) {
            try {
                JsonNode root = objectMapper.readTree(nextData.data());
                JsonNode pageProps = root.path("props").path("pageProps");

                JsonNode company = pageProps.path("initialEmploymentCompany");
                if (!company.isMissingNode()) {
                    if (companyName.isBlank()) {
                        companyName = company.path("name").asText("");
                    }

                    // 회사 추가 정보 추출하여 JD 보강
                    StringBuilder companyInfo = new StringBuilder();

                    String description = company.path("description").asText(
                        company.path("about").asText(""));
                    if (!description.isBlank()) {
                        companyInfo.append("[회사 소개]\n").append(description).append("\n\n");
                    }

                    String industry = company.path("industry").asText(
                        company.path("industry_name").asText(""));
                    if (!industry.isBlank()) {
                        companyInfo.append("[업종] ").append(industry).append("\n\n");
                    }

                    String homepage = company.path("homepage").asText(
                        company.path("website").asText(""));
                    if (!homepage.isBlank()) {
                        companyInfo.append("[홈페이지] ").append(homepage).append("\n\n");
                    }

                    // employments[] 배열에서 직무 정보 추출
                    JsonNode employments = company.path("employments");
                    if (employments.isArray() && !employments.isEmpty()) {
                        JsonNode emp = employments.get(0);

                        String field = emp.path("field").asText("");
                        String positionName = emp.path("title").asText(
                            emp.path("position_name").asText(""));
                        String department = emp.path("department").asText("");
                        String careerType = emp.path("career_type").asText("");

                        if (!field.isBlank()) {
                            companyInfo.append("[직무 분야] ").append(field).append("\n");
                        }
                        if (!positionName.isBlank()) {
                            companyInfo.append("[포지션] ").append(positionName).append("\n");
                        }
                        if (!department.isBlank()) {
                            companyInfo.append("[부서] ").append(department).append("\n");
                        }
                        if (!careerType.isBlank()) {
                            companyInfo.append("[경력 구분] ").append(careerType).append("\n");
                        }
                    }

                    if (!companyInfo.isEmpty()) {
                        jobDescription = companyInfo.toString();
                        log.info("자소설닷컴 __NEXT_DATA__에서 회사 상세 정보 추출 성공");
                    }
                }

                // __NEXT_DATA__에서 자소서 문항 추출 시도
                essayQuestions = extractEssayQuestionsFromJson(pageProps);

                // 문항 텍스트가 없으면, 인증 API로 실제 문항 텍스트 추출 시도
                if (essayQuestions.isEmpty()) {
                    StringBuilder authExtraInfo = new StringBuilder();
                    essayQuestions = extractQuestionsWithAuth(pageProps, authExtraInfo);
                    if (!authExtraInfo.isEmpty()) {
                        jobDescription = jobDescription.isBlank()
                            ? authExtraInfo.toString()
                            : jobDescription + "\n" + authExtraInfo;
                    }
                }

                // 인증 안 되면, employments의 resumes_count로 문항 수만 파악
                if (essayQuestions.isEmpty()) {
                    essayQuestions = extractQuestionCountFromEmployments(pageProps);
                }
            } catch (Exception e) {
                log.debug("자소설닷컴 __NEXT_DATA__ 파싱 스킵", e);
            }
        }

        // HTML body에서 정규식 폴백으로 문항 추출
        if (essayQuestions.isEmpty()) {
            essayQuestions = extractEssayQuestionsFromHtml(doc);
        }

        // 자소설닷컴 API 폴백: /api/v1/employment_companies/{id}
        if (essayQuestions.isEmpty()) {
            essayQuestions = extractQuestionsFromJasoseolApi(doc);
        }

        if (companyName.isBlank()) {
            companyName = doc.title().split("채용공고")[0].trim();
        }

        // 직무 설명 보강: og:description 또는 body
        if (jobDescription.isBlank()) {
            jobDescription = doc.select("meta[property=og:description]").attr("content");
        }
        if (jobDescription.isBlank()) {
            jobDescription = doc.select("meta[name=description]").attr("content");
        }
        if (jobDescription.isBlank()) {
            String bodyText = doc.body().text();
            jobDescription = bodyText.length() > 3000 ? bodyText.substring(0, 3000) : bodyText;
        }

        // 마감 여부: validThrough 기반 판단
        if (!deadline.isBlank()) {
            try {
                java.time.LocalDate dueDate = java.time.LocalDate.parse(deadline.substring(0, 10));
                if (dueDate.isBefore(java.time.LocalDate.now())) {
                    throw new CrawlingException("마감된 공고입니다. 마감일: " + deadline);
                }
            } catch (CrawlingException e) {
                throw e;
            } catch (Exception e) {
                log.debug("자소설닷컴 마감일 파싱 불가: {}", deadline);
            }
        }

        log.info("자소설닷컴 크롤링 성공 - 회사: {}, 마감: {}, 문항수: {}", companyName, deadline, essayQuestions.size());
        return CrawledJobInfo.of(companyName, jobDescription, requirements, deadline, true, essayQuestions);
    }

    private List<EssayQuestion> extractEssayQuestionsFromJson(JsonNode pageProps) {
        List<EssayQuestion> questions = new ArrayList<>();

        // 다양한 경로에서 문항 배열 탐색
        String[] candidatePaths = {
            "essayQuestions", "essay_questions", "questions",
            "selfIntroductionQuestions", "self_introduction_questions"
        };

        for (String path : candidatePaths) {
            JsonNode questionsNode = pageProps.path(path);
            if (questionsNode.isArray() && !questionsNode.isEmpty()) {
                questions = parseQuestionsArray(questionsNode);
                if (!questions.isEmpty()) return questions;
            }
        }

        // 중첩 경로 탐색: initialEmployment -> essayQuestions 등
        String[] parentPaths = {"initialEmployment", "employment", "jobPosting", "recruit"};
        for (String parent : parentPaths) {
            JsonNode parentNode = pageProps.path(parent);
            if (parentNode.isMissingNode()) continue;
            for (String path : candidatePaths) {
                JsonNode questionsNode = parentNode.path(path);
                if (questionsNode.isArray() && !questionsNode.isEmpty()) {
                    questions = parseQuestionsArray(questionsNode);
                    if (!questions.isEmpty()) return questions;
                }
            }
        }

        return questions;
    }

    private List<EssayQuestion> parseQuestionsArray(JsonNode questionsNode) {
        List<EssayQuestion> questions = new ArrayList<>();
        int index = 1;
        for (JsonNode q : questionsNode) {
            String text = q.path("question").asText(
                q.path("title").asText(
                    q.path("questionText").asText(
                        q.path("content").asText(""))));
            int charLimit = q.path("charLimit").asInt(
                q.path("char_limit").asInt(
                    q.path("maxLength").asInt(
                        q.path("max_length").asInt(0))));
            int number = q.path("number").asInt(
                q.path("order").asInt(index));

            if (!text.isBlank()) {
                questions.add(new EssayQuestion(number, text.trim(), charLimit));
                index++;
            }
        }
        return questions;
    }

    /**
     * 자소설닷컴 인증 API를 이용하여 실제 문항 텍스트를 추출합니다.
     * 응답에서 employment 관련 추가 정보도 추출하여 extraInfo에 저장합니다.
     */
    private List<EssayQuestion> extractQuestionsWithAuth(JsonNode pageProps, StringBuilder extraInfo) {
        if (jasoseolUserToken == null || jasoseolUserToken.isBlank()) {
            return List.of();
        }

        JsonNode company = pageProps.path("initialEmploymentCompany");
        if (company.isMissingNode()) return List.of();

        JsonNode employments = company.path("employments");
        if (!employments.isArray() || employments.isEmpty()) return List.of();

        int employmentId = employments.get(0).path("id").asInt(0);
        if (employmentId == 0) return List.of();

        log.info("자소설닷컴 인증 API로 문항 추출 시도 (employment_id={})", employmentId);

        try {
            // Cookie 인증으로 new_employment.json 호출 (문항 정보 포함 응답, 실제 저장 안 됨)
            String requestBody = "{\"employment_id\":" + employmentId + "}";
            Connection.Response resp = Jsoup.connect("https://jasoseol.com/resume/new_employment.json")
                .userAgent(USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .cookie("jssUserToken", jasoseolUserToken)
                .requestBody(requestBody)
                .method(Connection.Method.POST)
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .timeout(10_000)
                .execute();

            if (resp.statusCode() != 200) {
                log.warn("자소설닷컴 인증 API 응답 코드: {} (토큰 만료 가능성)", resp.statusCode());
                return List.of();
            }

            JsonNode data = objectMapper.readTree(resp.body());

            // 응답에서 employment 관련 추가 정보 추출
            if (extraInfo != null) {
                String empTitle = data.path("title").asText(data.path("position_name").asText(""));
                String empField = data.path("field").asText("");
                String empDepartment = data.path("department").asText("");
                String empCareerType = data.path("career_type").asText("");
                String empDescription = data.path("description").asText("");

                if (!empTitle.isBlank()) extraInfo.append("[포지션] ").append(empTitle).append("\n");
                if (!empField.isBlank()) extraInfo.append("[직무 분야] ").append(empField).append("\n");
                if (!empDepartment.isBlank()) extraInfo.append("[부서] ").append(empDepartment).append("\n");
                if (!empCareerType.isBlank()) extraInfo.append("[경력 구분] ").append(empCareerType).append("\n");
                if (!empDescription.isBlank()) extraInfo.append("[채용 설명]\n").append(empDescription).append("\n");
            }

            // qnas 배열에서 문항 추출
            JsonNode qnas = data.path("qnas");
            if (qnas.isArray() && !qnas.isEmpty()) {
                List<EssayQuestion> questions = new ArrayList<>();
                for (JsonNode qna : qnas) {
                    int number = qna.path("number").asInt(0);
                    String questionText = qna.path("question").asText("");
                    int charLimit = qna.path("total_count").asInt(0);

                    if (!questionText.isBlank()) {
                        questions.add(new EssayQuestion(number, questionText.trim(), charLimit));
                    }
                }

                if (!questions.isEmpty()) {
                    log.info("자소설닷컴 인증 API로 문항 {}개 추출 성공", questions.size());
                    return questions;
                }
            }

            log.debug("자소설닷컴 API 응답에 qnas 없음: {}", resp.body().substring(0, Math.min(300, resp.body().length())));
        } catch (Exception e) {
            log.warn("자소설닷컴 인증 API 문항 추출 실패: {}", e.getMessage());
            log.debug("상세 오류", e);
        }

        return List.of();
    }

    private List<EssayQuestion> extractQuestionCountFromEmployments(JsonNode pageProps) {
        List<EssayQuestion> questions = new ArrayList<>();
        JsonNode company = pageProps.path("initialEmploymentCompany");
        if (company.isMissingNode()) return questions;

        JsonNode employments = company.path("employments");
        if (!employments.isArray()) return questions;

        for (JsonNode emp : employments) {
            int resumeCount = emp.path("resumes_count").asInt(
                emp.path("resume_count").asInt(0));
            if (resumeCount > 0) {
                String field = emp.path("field").asText("");
                log.info("자소설닷컴 문항 {}개 감지 (직무: {}) - 문항 텍스트는 로그인 필요", resumeCount, field);
                for (int i = 1; i <= resumeCount; i++) {
                    questions.add(new EssayQuestion(i,
                        "자소서 문항 " + i + " (자소설닷컴 로그인 후 확인 가능)", 0));
                }
                break; // 첫 번째 employment만 처리
            }
        }
        return questions;
    }

    private List<EssayQuestion> extractQuestionsFromJasoseolApi(Document doc) {
        List<EssayQuestion> questions = new ArrayList<>();
        // URL에서 recruit ID 추출
        String canonical = doc.select("link[rel=canonical]").attr("href");
        if (canonical.isBlank()) {
            canonical = doc.select("meta[property=og:url]").attr("content");
        }

        String recruitId = "";
        java.util.regex.Matcher matcher = Pattern.compile("/recruit/(\\d+)").matcher(canonical);
        if (matcher.find()) {
            recruitId = matcher.group(1);
        }
        if (recruitId.isBlank()) return questions;

        try {
            String apiUrl = "https://jasoseol.com/api/v1/employment_companies/" + recruitId;
            Document apiDoc = Jsoup.connect(apiUrl)
                .userAgent(USER_AGENT)
                .header("Accept", "application/json")
                .ignoreContentType(true)
                .timeout(5_000)
                .get();

            JsonNode apiData = objectMapper.readTree(apiDoc.body().text());
            JsonNode employments = apiData.path("employments");
            if (employments.isArray()) {
                for (JsonNode emp : employments) {
                    int resumeCount = emp.path("resumes_count").asInt(0);
                    if (resumeCount > 0) {
                        String field = emp.path("field").asText("");
                        log.info("자소설닷컴 API에서 문항 {}개 확인 (직무: {})", resumeCount, field);
                        for (int i = 1; i <= resumeCount; i++) {
                            questions.add(new EssayQuestion(i,
                                "자소서 문항 " + i + " (자소설닷컴 로그인 후 확인 가능)", 0));
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("자소설닷컴 API 폴백 실패: {}", e.getMessage());
        }
        return questions;
    }

    private List<EssayQuestion> extractEssayQuestionsFromHtml(Document doc) {
        List<EssayQuestion> questions = new ArrayList<>();
        String bodyText = doc.body().text();
        Matcher matcher = ESSAY_QUESTION_PATTERN.matcher(bodyText);

        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            String questionText = matcher.group(2).trim();
            int charLimit = Integer.parseInt(matcher.group(3));
            questions.add(new EssayQuestion(number, questionText, charLimit));
        }

        if (!questions.isEmpty()) {
            log.info("HTML 정규식으로 자소서 문항 {} 개 추출", questions.size());
        }
        return questions;
    }

    private String extractCompanyName(Document doc, String url) {
        // 잡코리아
        if (url.contains("jobkorea.co.kr")) {
            return doc.select(".company-name").text();
        }
        // 링커리어
        if (url.contains("linkareer.com")) {
            return doc.select(".organization-name").text();
        }
        return doc.title();
    }

    private String extractJobDescription(Document doc) {
        // 메타 description 먼저 시도
        String meta = doc.select("meta[name=description]").attr("content");
        if (!meta.isBlank()) {
            return meta;
        }
        // body 텍스트에서 주요 내용 추출 (앞 3000자)
        String bodyText = doc.body().text();
        return bodyText.length() > 3000 ? bodyText.substring(0, 3000) : bodyText;
    }

    private String extractRequirements(Document doc) {
        // 자격요건 섹션 파싱 시도
        String requirements = doc.select("[class*=requirement], [class*=qualify]").text();
        return requirements.isBlank() ? "" : requirements;
    }
}
