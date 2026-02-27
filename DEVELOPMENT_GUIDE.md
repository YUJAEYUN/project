# 개발 지침서 (Development Guide)

> **원칙**: 각 단계의 모든 테스트가 통과해야 다음 단계로 진행할 수 있습니다.
> **API 문서**: Swagger UI (`/swagger-ui.html`) 를 통해 제공합니다.

---

## 0. 실패하면 안 되는 우선순위 기준

### 판단 근거

기능을 아래 3가지 축으로 평가합니다.

| 축 | 설명 |
|----|------|
| **시연 사망 여부** | 이 기능이 없으면 고객 앞에서 아무것도 보여줄 수 없는가 |
| **의존성 깊이** | 다른 기능이 이 기능에 얼마나 의존하는가 |
| **복구 가능성** | 실패 시 다른 방법으로 우회·대체할 수 있는가 |

### 우선순위 티어

```
┌─────────────────────────────────────────────────┐
│  TIER 0 — 절대 실패 불가 (시스템 기반)            │
│  인증(회원가입·로그인·JWT 필터)                    │
│  → 없으면 모든 API가 동작 불가                    │
├─────────────────────────────────────────────────┤
│  TIER 1 — 시연 핵심 (데모 가치)                  │
│  대화 생성 (OpenAI 연동 + 스레드 로직)            │
│  → 없으면 "AI 챗봇"이라고 부를 수 없음            │
├─────────────────────────────────────────────────┤
│  TIER 2 — 시연 완성도 (제품 느낌)                │
│  대화 목록 조회 + 스레드 삭제                     │
│  → 없으면 동작은 하지만 단발성 데모에 그침         │
├─────────────────────────────────────────────────┤
│  TIER 3 — 부가 가치 (운영 품질)                  │
│  피드백 관리 (생성·목록·상태변경)                  │
│  → 없어도 데모는 성공, 있으면 신뢰도 상승          │
├─────────────────────────────────────────────────┤
│  TIER 4 — 관리자 도구 (운영 편의)                │
│  활동 통계 + CSV 보고서                           │
│  → 시연과 직접 관련 없음, 시간 여유 시 구현        │
└─────────────────────────────────────────────────┘
```

> **규칙**: TIER N 의 모든 테스트가 GREEN 이 되어야 TIER N+1 을 시작합니다.

---

## 1. 기술 스택

```
Language  : Kotlin 1.9.x
Framework : Spring Boot 3.x
Build     : Gradle (Kotlin DSL)
DB        : H2 (local·test) / PostgreSQL 15.8+ (prod)
ORM       : Spring Data JPA + Hibernate
Auth      : Spring Security 6 + JWT (JJWT)
HTTP Client : Spring WebClient (OpenAI 연동·SSE)
API Docs  : springdoc-openapi 2.x (Swagger UI)
Test      : JUnit 5 + MockK + Spring Boot Test + MockMvc
```

---

## 2. 프로젝트 구조

```
src/
├── main/kotlin/com/example/chatbot/
│   ├── ChatbotApplication.kt
│   ├── common/
│   │   ├── config/          # Security, WebClient, Swagger 설정
│   │   ├── exception/       # 전역 예외 처리 (GlobalExceptionHandler)
│   │   ├── response/        # 공통 응답 래퍼 (ApiResponse, ErrorResponse)
│   │   └── security/        # JwtProvider, JwtFilter, UserDetailsService
│   ├── domain/
│   │   ├── auth/            # TIER 0
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── entity/
│   │   │   └── dto/
│   │   ├── chat/            # TIER 1, 2
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── entity/
│   │   │   └── dto/
│   │   ├── feedback/        # TIER 3
│   │   │   └── ...
│   │   └── analytics/       # TIER 4
│   │       └── ...
│   └── infrastructure/
│       └── openai/          # OpenAI WebClient 래퍼
│           ├── OpenAiClient.kt
│           └── dto/
└── test/kotlin/com/example/chatbot/
    ├── common/              # 테스트 픽스처, 헬퍼
    ├── domain/
    │   ├── auth/
    │   ├── chat/
    │   ├── feedback/
    │   └── analytics/
    └── integration/         # E2E 통합 테스트 (선택)
```

---

## 3. Swagger 설정

### 의존성 (build.gradle.kts)

```kotlin
dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
}
```

### 설정 클래스

```kotlin
// common/config/SwaggerConfig.kt
@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("AI Chatbot API")
                .description("VIP Onboarding 챗봇 서비스 API 명세")
                .version("v1.0.0")
        )
        .addSecurityItem(SecurityRequirement().addList("BearerAuth"))
        .components(
            Components().addSecuritySchemes(
                "BearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("로그인 후 발급된 JWT 토큰을 입력하세요.")
            )
        )
}
```

### application.yml

```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    operationsSorter: method
    disable-swagger-default-url: true
  default-consumes-media-type: application/json
  default-produces-media-type: application/json
```

### Security에서 Swagger 경로 허용

```kotlin
// PERMIT_ALL 목록에 추가
"/swagger-ui/**", "/api-docs/**", "/swagger-ui.html"
```

### 컨트롤러 어노테이션 규칙

```kotlin
@Tag(name = "Auth", description = "인증 API")
@RestController
class AuthController {

    @Operation(
        summary = "회원가입",
        description = "이메일, 패스워드, 이름을 입력받아 회원을 생성합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "회원가입 성공"),
        ApiResponse(responseCode = "409", description = "이메일 중복"),
        ApiResponse(responseCode = "400", description = "요청 형식 오류")
    )
    @PostMapping("/auth/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<UserResponse> { ... }
}
```

---

## 4. 테스트 전략

### 4-1. 테스트 레이어

| 레이어 | 대상 | 도구 | 목적 |
|--------|------|------|------|
| Unit | Service, Domain Logic | JUnit5 + MockK | 비즈니스 규칙 검증 |
| Slice | Controller | MockMvc + @WebMvcTest | HTTP 계층 검증 |
| Integration | Repository | @DataJpaTest + H2 | DB 쿼리·제약 검증 |

> OpenAI API 호출은 반드시 MockK로 모킹합니다. 실제 API를 호출하는 테스트는 작성하지 않습니다.

### 4-2. 테스트 파일 네이밍 규칙

```
{대상클래스}Test.kt          # Unit / Slice 테스트
{대상클래스}IntegrationTest.kt  # Integration 테스트
```

### 4-3. 테스트 커버리지 최소 기준

각 TIER 통과 조건:

| 항목 | 기준 |
|------|------|
| 모든 테스트 PASS | 필수 |
| 정상 케이스 (Happy Path) | 각 API 엔드포인트당 1개 이상 |
| 에러 케이스 | PRD에 정의된 에러 코드당 1개 이상 |
| 권한 검증 | 인증 없는 요청, 권한 부족 요청 각 1개 이상 |

---

## 5. 단계별 구현 체크리스트

---

### ✅ STEP 0 — 프로젝트 셋업

> 다음 단계로 가기 전 완료 확인

- [ ] Spring Boot 3.x + Kotlin 1.9.x Gradle 프로젝트 생성
- [ ] 의존성 추가: Security, JPA, H2, PostgreSQL, JJWT, springdoc, WebClient, Validation
- [ ] 프로파일 분리: `application-local.yml` (H2), `application-prod.yml` (PostgreSQL)
- [ ] 공통 응답 구조 작성
  ```kotlin
  data class ApiResponse<T>(val data: T)
  data class ErrorResponse(val code: String, val message: String)
  ```
- [ ] `GlobalExceptionHandler` 작성 (`@RestControllerAdvice`)
- [ ] Swagger 설정 완료 → `/swagger-ui.html` 접근 확인
- [ ] `./gradlew test` → 컴파일 에러 없음 확인

---

### ✅ STEP 1 — TIER 0: 인증 (Auth)

#### 구현 목록

- [ ] `User` 엔티티 + `UserRepository`
- [ ] `POST /api/v1/auth/signup`
- [ ] `POST /api/v1/auth/login`
- [ ] `JwtProvider` (토큰 생성·검증)
- [ ] `JwtAuthenticationFilter` (요청별 토큰 파싱)
- [ ] `SecurityConfig` (permitAll / authenticated 경로 분리)
- [ ] Swagger `@Tag`, `@Operation`, `@ApiResponse` 어노테이션 작성

#### 필수 테스트 목록

```
AuthControllerTest
├── [POST /auth/signup]
│   ├── 정상_회원가입_성공_201
│   ├── 이메일_중복_409
│   ├── 이메일_형식_오류_400
│   ├── 패스워드_누락_400
│   └── 이름_누락_400
├── [POST /auth/login]
│   ├── 정상_로그인_성공_JWT_발급_200
│   ├── 이메일_불일치_401
│   └── 패스워드_불일치_401
└── [JWT 필터]
    ├── 토큰_없이_인증필요_API_접근_401
    ├── 만료된_토큰_401
    └── 변조된_토큰_401

UserRepositoryTest
├── 이메일_중복_저장_예외
└── 동일_이메일_조회_성공

JwtProviderTest
├── 토큰_생성_후_파싱_성공
├── 만료된_토큰_검증_실패
└── 변조된_토큰_검증_실패
```

#### STEP 1 통과 기준

```bash
./gradlew test --tests "*.auth.*"
# BUILD SUCCESSFUL — 모든 테스트 GREEN
```

---

### ✅ STEP 2 — TIER 1: 대화 생성 (Chat Create)

#### 구현 목록

- [ ] `Thread` 엔티티 + `ThreadRepository`
- [ ] `Chat` 엔티티 + `ChatRepository`
- [ ] `OpenAiClient` (WebClient 래퍼)
  - non-streaming: `POST /v1/chat/completions`
  - streaming: SSE 응답 처리
- [ ] `ThreadService` — 30분 스레드 결정 로직
- [ ] `ChatService.createChat()`
  - 스레드 결정 → 히스토리 조회 → OpenAI 요청 → 저장 → 응답
- [ ] `POST /api/v1/chats`
- [ ] Swagger 어노테이션 작성

#### 스레드 30분 로직 상세

```
fun resolveThread(userId: UUID): Thread {
    val latest = threadRepository.findTopByUserIdOrderByLastChatAtDesc(userId)
    return if (latest == null || ChronoUnit.MINUTES.between(latest.lastChatAt, now()) > 30) {
        threadRepository.save(Thread.create(userId))
    } else {
        latest
    }
}
```

#### 필수 테스트 목록

```
ChatServiceTest (Unit — OpenAiClient MockK)
├── [스레드 로직]
│   ├── 첫_질문_새_스레드_생성
│   ├── 30분_이내_기존_스레드_재사용
│   ├── 30분_초과_새_스레드_생성
│   └── 정확히_30분_경계값_새_스레드_생성
├── [대화 생성]
│   ├── 정상_대화_생성_성공
│   ├── OpenAI_오류_502_반환
│   └── 이전_대화_히스토리_messages_포함_확인

ChatControllerTest (MockMvc)
├── [POST /api/v1/chats]
│   ├── 정상_non_streaming_201
│   ├── 정상_streaming_text/event-stream
│   ├── question_누락_400
│   ├── 인증_없음_401
│   └── 커스텀_model_파라미터_적용

OpenAiClientTest (MockWebServer or MockK)
├── non_streaming_응답_파싱_성공
├── streaming_chunk_파싱_성공
└── API_오류_응답_예외_변환
```

#### STEP 2 통과 기준

```bash
./gradlew test --tests "*.chat.*"
# BUILD SUCCESSFUL — 모든 테스트 GREEN
```

---

### ✅ STEP 3 — TIER 2: 대화 목록 조회 & 스레드 삭제

#### 구현 목록

- [ ] `GET /api/v1/chats` (스레드 그룹화 + 페이지네이션 + 정렬 + 역할별 필터)
- [ ] `DELETE /api/v1/threads/{threadId}` (소유권 검증 + cascade)
- [ ] Swagger 어노테이션 작성

#### 필수 테스트 목록

```
ChatControllerTest
├── [GET /api/v1/chats]
│   ├── MEMBER_본인_스레드_목록_조회
│   ├── ADMIN_전체_스레드_목록_조회
│   ├── ADMIN_userId_필터_조회
│   ├── 페이지네이션_page_size_동작
│   ├── createdAt_asc_정렬
│   ├── createdAt_desc_정렬
│   ├── 스레드_내_chat_목록_포함_확인
│   └── 인증_없음_401
├── [DELETE /api/v1/threads/{threadId}]
│   ├── 본인_스레드_삭제_204
│   ├── 타인_스레드_삭제_시도_403
│   ├── ADMIN_타인_스레드_삭제_204
│   ├── 존재하지_않는_스레드_404
│   └── 연관_Chat_cascade_삭제_확인
```

#### STEP 3 통과 기준

```bash
./gradlew test --tests "*.chat.*"
# BUILD SUCCESSFUL — 모든 테스트 GREEN
```

---

### ✅ STEP 4 — TIER 3: 피드백 관리

#### 구현 목록

- [ ] `Feedback` 엔티티 + `FeedbackRepository`
- [ ] `POST /api/v1/feedbacks`
- [ ] `GET /api/v1/feedbacks`
- [ ] `PATCH /api/v1/feedbacks/{feedbackId}/status`
- [ ] Swagger 어노테이션 작성

#### 필수 테스트 목록

```
FeedbackControllerTest
├── [POST /api/v1/feedbacks]
│   ├── 본인_대화_피드백_생성_201
│   ├── ADMIN_타인_대화_피드백_생성_201
│   ├── MEMBER_타인_대화_피드백_403
│   ├── 존재하지_않는_chatId_404
│   ├── 동일_대화_중복_피드백_409
│   └── 인증_없음_401
├── [GET /api/v1/feedbacks]
│   ├── MEMBER_본인_피드백_목록
│   ├── ADMIN_전체_피드백_목록
│   ├── isPositive_true_필터
│   ├── isPositive_false_필터
│   ├── 페이지네이션_동작
│   └── createdAt_정렬
└── [PATCH /api/v1/feedbacks/{feedbackId}/status]
    ├── ADMIN_상태_변경_성공_200
    ├── MEMBER_상태_변경_시도_403
    └── 존재하지_않는_feedbackId_404
```

#### STEP 4 통과 기준

```bash
./gradlew test --tests "*.feedback.*"
# BUILD SUCCESSFUL — 모든 테스트 GREEN
```

---

### ✅ STEP 5 — TIER 4: 분석 & 보고

#### 구현 목록

- [ ] `ActivityLog` 엔티티 + 로그 기록 (회원가입·로그인·대화생성 시점)
- [ ] `GET /api/v1/admin/analytics/activity`
- [ ] `GET /api/v1/admin/analytics/report` (CSV 다운로드)
- [ ] Swagger 어노테이션 작성

#### 필수 테스트 목록

```
AnalyticsControllerTest
├── [GET /admin/analytics/activity]
│   ├── ADMIN_24시간_통계_조회_성공
│   ├── MEMBER_접근_403
│   └── 응답_from_to_범위_24시간_확인
└── [GET /admin/analytics/report]
    ├── ADMIN_CSV_다운로드_성공
    ├── CSV_헤더_컬럼_확인
    ├── CSV_데이터_행_확인
    └── MEMBER_접근_403
```

#### STEP 5 통과 기준

```bash
./gradlew test --tests "*.analytics.*"
# BUILD SUCCESSFUL — 모든 테스트 GREEN
```

---

### ✅ STEP 6 — 전체 통합 확인

```bash
# 모든 테스트 실행
./gradlew test

# 결과 확인
# BUILD SUCCESSFUL
# X tests completed, 0 failed
```

- [ ] 전체 테스트 GREEN 확인
- [ ] Swagger UI에서 모든 API 확인 (`/swagger-ui.html`)
- [ ] `local` 프로파일로 서버 기동 후 Swagger에서 직접 API 호출 확인
- [ ] README.md 작성 (실행 방법, 환경변수, API 접근 URL)

---

## 6. 공통 코딩 규칙

### 응답 HTTP 상태 코드

| 상황 | 코드 |
|------|------|
| 생성 성공 | 201 |
| 조회·수정 성공 | 200 |
| 삭제 성공 | 204 |
| 잘못된 요청 | 400 |
| 인증 실패 | 401 |
| 권한 없음 | 403 |
| 리소스 없음 | 404 |
| 충돌 (중복) | 409 |
| 외부 API 오류 | 502 |

### 예외 처리 규칙

- 커스텀 예외는 `domain.{영역}.exception` 패키지에 위치
- 모든 예외는 `GlobalExceptionHandler`에서 `ErrorResponse`로 변환
- 예외 클래스명 = 에러 코드와 1:1 대응

```kotlin
// 예시
class EmailDuplicatedException : BusinessException("EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다.")
class ChatNotFoundException : BusinessException("CHAT_NOT_FOUND", "존재하지 않는 대화입니다.")
```

### 엔티티 규칙

- ID: `UUID.randomUUID()`
- `createdAt`: `@CreationTimestamp` + `OffsetDateTime`
- 연관관계 삭제: Thread 삭제 시 Chat `CascadeType.ALL` + `orphanRemoval = true`

### 테스트 픽스처 규칙

```kotlin
// test/common/TestFixtures.kt 에 공통 픽스처 정의
object TestFixtures {
    fun createUser(role: Role = Role.MEMBER) = User(...)
    fun createThread(user: User) = Thread(...)
    fun createChat(thread: Thread, user: User) = Chat(...)
}
```

---

## 7. 환경 설정

### application-local.yml (H2)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:chatbot;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  h2:
    console:
      enabled: true

openai:
  api-key: ${OPENAI_API_KEY}
  base-url: https://api.openai.com/v1
  default-model: gpt-4o

jwt:
  secret: ${JWT_SECRET:local-dev-secret-key-minimum-256-bits-long}
  expiration-ms: 3600000
```

### 테스트용 application.yml

```yaml
# src/test/resources/application.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL
  jpa:
    hibernate:
      ddl-auto: create-drop

openai:
  api-key: test-api-key
  default-model: gpt-4o-mini

jwt:
  secret: test-secret-key-minimum-256-bits-long-enough
  expiration-ms: 3600000
```

---

## 8. 빠른 참조

### 단계별 진행 명령

```bash
# STEP 별 테스트 실행
./gradlew test --tests "*.auth.*"       # STEP 1
./gradlew test --tests "*.chat.*"       # STEP 2, 3
./gradlew test --tests "*.feedback.*"   # STEP 4
./gradlew test --tests "*.analytics.*"  # STEP 5
./gradlew test                          # STEP 6 최종

# 서버 실행
./gradlew bootRun --args='--spring.profiles.active=local'

# Swagger 접근
open http://localhost:8080/swagger-ui.html
```

### 체크포인트 요약

| STEP | 내용 | 통과 기준 |
|------|------|-----------|
| 0 | 프로젝트 셋업 | 컴파일 성공 + Swagger 접근 |
| 1 | 인증 (TIER 0) | `*.auth.*` 테스트 ALL GREEN |
| 2 | 대화 생성 (TIER 1) | `*.chat.*` 테스트 ALL GREEN |
| 3 | 대화 목록·삭제 (TIER 2) | `*.chat.*` 테스트 ALL GREEN |
| 4 | 피드백 (TIER 3) | `*.feedback.*` 테스트 ALL GREEN |
| 5 | 분석·보고 (TIER 4) | `*.analytics.*` 테스트 ALL GREEN |
| 6 | 전체 통합 | `./gradlew test` ALL GREEN |
