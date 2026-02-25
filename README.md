# Career Assistant

취업 준비를 위한 올인원 AI 어시스턴트. 채용공고 크롤링부터 자소서 생성, GitHub 학습 현황 추적, 텔레그램 리마인더까지 하나의 서비스로 관리합니다.

## 주요 기능

### 자소서 자동 생성 파이프라인
채용공고 URL을 입력하면 크롤링 → 기업 분류 → 기업 분석 → AI 자소서 생성 → 리뷰 반복 개선까지 전 과정을 자동으로 수행합니다.
- JSoup 기반 채용공고 크롤링
- 기업 유형별(대기업/중견/스타트업/금융) Claude AI 모델 라우팅
- 문항별 자소서 생성 + AI 리뷰 에이전트의 반복 개선 (최대 3회, 85점 이상 시 종료)
- 버전 관리 및 피드백 이력 저장

### GitHub 학습 현황 추적
coding-test, 블로그, cs-study 레포의 커밋을 분석하여 학습 활동 상태를 자동 추적합니다.
- **coding-test**: BaekjoonHub 커밋 메시지 패턴으로 백준 티어 / 프로그래머스 레벨 자동 분류
- **블로그**: `docs:`/`feat:` 커밋만 카운트, 배포 커밋(`chore: deploy`) 자동 제외
- **cs-study**: 파일 경로의 최상위 디렉토리를 토픽으로 분류 (미생성 레포는 graceful skip)
- 활동 상태: ACTIVE(7일 이내) / WARNING(8~14일) / DORMANT(15일 이상)

### 텔레그램 리마인더
- 매일 오전 9시 모닝 브리핑
- 매일 밤 10시 이브닝 체크

### 경험 관리
프로젝트, 수상, 업무 경험 등을 카테고리별로 저장하여 자소서 생성 시 참고 자료로 활용합니다.

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.0 |
| Database | MySQL 8 + Spring Data JPA |
| AI | Claude API (Sonnet 4.6 / Haiku 4.5) |
| HTTP Client | Spring WebFlux WebClient |
| Crawling | JSoup 1.17.2 |
| Bot | Telegram Bot API 6.9.7.1 |
| Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Gradle |

## 프로젝트 구조

```
com.career.assistant
├── api/                          # REST 컨트롤러 & DTO
│   ├── CoverLetterController     # 자소서 생성/조회 API
│   ├── JobPostingController      # 채용공고 조회 API
│   ├── UserExperienceController  # 경험 관리 API
│   ├── GitHubActivityController  # GitHub 활동 조회 API
│   └── dto/                      # 요청/응답 DTO
├── application/                  # 비즈니스 로직
│   ├── CoverLetterFacade         # 자소서 생성 파이프라인 오케스트레이션
│   ├── CompanyAnalyzer           # 기업 분석
│   ├── CompanyClassifier         # 기업 유형 분류
│   ├── CoverLetterPromptBuilder  # 프롬프트 빌더
│   ├── review/ReviewAgent        # AI 리뷰 에이전트
│   └── github/GitHubAnalyzer     # GitHub 커밋 분석 서비스
├── domain/                       # JPA 엔티티 & 리포지토리
│   ├── jobposting/               # 채용공고 (JobPosting, CompanyType, PipelineStatus)
│   ├── coverletter/              # 자소서 (CoverLetter)
│   ├── experience/               # 경험 (UserExperience, ExperienceCategory)
│   └── github/                   # GitHub 활동 (GitHubActivity, ActivityStatus)
├── infrastructure/               # 외부 연동
│   ├── ai/                       # Claude API (AiPort, AiRouter, ClaudeAdapter)
│   ├── crawling/                 # JSoup 크롤러
│   ├── github/                   # GitHub REST API 클라이언트
│   └── telegram/                 # 텔레그램 봇
├── scheduler/                    # 스케줄러
│   ├── DailyReminderScheduler    # 텔레그램 리마인더 (9AM, 10PM)
│   └── GitHubSyncScheduler       # GitHub 동기화 (매일 7AM)
└── common/
    └── AppConfig                 # WebClient, Claude 빈 설정
```

## API 엔드포인트

### 자소서 생성
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/cover-letters/preview?url=` | 채용공고 URL 크롤링 미리보기 |
| POST | `/api/cover-letters` | 자소서 생성 (body: `{ "url": "..." }`) |
| GET | `/api/cover-letters?jobPostingId=` | 채용공고별 자소서 조회 |
| GET | `/api/cover-letters/{id}` | 자소서 단건 조회 |
| GET | `/api/cover-letters/agent-result?jobPostingId=` | AI 리뷰 반복 이력 조회 |
| GET | `/api/cover-letters/analysis?jobPostingId=` | 기업 분석 및 작성 가이드라인 조회 |

### 채용공고
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/job-postings` | 전체 채용공고 조회 |
| GET | `/api/job-postings/{id}` | 채용공고 단건 조회 |
| DELETE | `/api/job-postings/{id}` | 채용공고 삭제 |

### 경험 관리
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/experiences` | 전체 경험 조회 (카테고리 필터 가능) |
| GET | `/api/experiences/{id}` | 경험 단건 조회 |
| POST | `/api/experiences` | 경험 등록 |
| DELETE | `/api/experiences/{id}` | 경험 삭제 |

### GitHub 활동
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/github/activities` | 전체 레포 활동 현황 조회 |
| GET | `/api/github/activities/{repoName}` | 레포별 활동 현황 조회 |
| POST | `/api/github/sync` | 수동 동기화 트리거 |

> Swagger UI: `http://localhost:8080/swagger-ui.html`

## 실행 방법

### 사전 준비
- Java 21
- MySQL 8
- Gradle

### 1. DB 생성
```sql
CREATE DATABASE career_assistant;
```

### 2. 환경 설정
`.env.example`을 참고하여 `application.yml`을 작성합니다.

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/career_assistant
    username: root
    password: <비밀번호>
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update

telegram:
  bot-token: <BotFather에서 받은 토큰>
  chat-id: <본인 Chat ID>

ai:
  claude:
    api-key: <Claude API 키>
    sonnet-model: claude-sonnet-4-6
    haiku-model: claude-haiku-4-5-20251001
    base-url: https://api.anthropic.com/v1/messages

github:
  token: ${GITHUB_TOKEN:}
  username: <GitHub 사용자명>
  repos:
    coding-test: <코딩테스트 레포명>
    blog: <블로그 레포명>
    cs-study: <CS 스터디 레포명>
```

### 3. 빌드 및 실행
```bash
./gradlew build -x test
./gradlew bootRun
```

### 4. 확인
```bash
# Swagger UI
open http://localhost:8080/swagger-ui.html

# GitHub 동기화 테스트
curl -X POST http://localhost:8080/api/github/sync

# GitHub 활동 조회
curl http://localhost:8080/api/github/activities
```

## 환경 변수

| 변수 | 설명 | 필수 |
|------|------|------|
| `DB_URL` | MySQL 접속 URL | O |
| `DB_USERNAME` | DB 사용자명 | O |
| `DB_PASSWORD` | DB 비밀번호 | O |
| `TELEGRAM_BOT_TOKEN` | 텔레그램 봇 토큰 | O |
| `TELEGRAM_CHAT_ID` | 텔레그램 채팅 ID | O |
| `CLAUDE_API_KEY` | Claude API 키 | O |
| `GITHUB_TOKEN` | GitHub Personal Access Token | - |

## DB 스키마

| 테이블 | 설명 |
|--------|------|
| `job_postings` | 채용공고 (URL, 기업명, 기업유형, JD, 자격요건, 에세이 문항, 기업분석, 파이프라인 상태) |
| `cover_letters` | 자소서 (채용공고 FK, AI 모델, 본문, 버전, 문항 인덱스, 피드백, 리뷰 점수) |
| `user_experiences` | 사용자 경험 (카테고리, 제목, 설명, 스킬, 기간) |
| `github_activities` | GitHub 활동 (레포명+토픽 유니크, 마지막 커밋일, 커밋수, 경과일, 활동상태) |
| `pipeline_log` | 파이프라인 로그 (채용공고 FK, 단계, 상태, 에러메시지) |

## 배포

Railway/Heroku 배포 시 Procfile이 사용됩니다.

```
web: java -jar build/libs/assistant-0.0.1-SNAPSHOT.jar
```
