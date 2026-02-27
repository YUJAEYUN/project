# AI Chatbot API

Kotlin 1.9 + Spring Boot 3.2 기반 AI 챗봇 REST API 서버입니다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Kotlin 1.9.22 |
| Framework | Spring Boot 3.2.3 |
| Build | Gradle 8.6 (Kotlin DSL) |
| DB (로컬) | H2 in-memory |
| DB (운영) | PostgreSQL 15.8+ |
| Auth | Spring Security 6 + JWT (JJWT 0.12) |
| AI | OpenAI API (WebClient) |
| API 문서 | Springdoc OpenAPI 2.x (Swagger UI) |
| Test | JUnit 5 + MockK + Spring MockMvc |

---

## 실행 방법

### 1. 환경변수 설정

```bash
export OPENAI_API_KEY=sk-...
export JWT_SECRET=your-256-bit-secret-key-here-make-it-long-enough
```

필요 시 추가 환경변수:

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `OPENAI_API_KEY` | OpenAI API 키 | (필수) |
| `OPENAI_DEFAULT_MODEL` | 기본 모델 | `gpt-4o` |
| `OPENAI_BASE_URL` | OpenAI base URL | `https://api.openai.com/v1` |
| `JWT_SECRET` | JWT 서명 시크릿 (256bit 이상) | (필수) |
| `JWT_EXPIRATION_MS` | JWT 만료 시간 (ms) | `3600000` (1시간) |

### 2. 로컬 실행 (H2 in-memory DB)

```bash
./gradlew bootRun
```

> 기본 프로파일: `local` (H2 사용)

### 3. 운영 실행 (PostgreSQL)

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://localhost:5432/chatbot
export DB_USERNAME=postgres
export DB_PASSWORD=your-password

./gradlew bootRun
```

---

## 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 도메인별 테스트
./gradlew test   # 전체
```

테스트 결과 리포트: `build/reports/tests/test/index.html`

---

## API 문서 (Swagger UI)

서버 실행 후 브라우저에서 접속:

```
http://localhost:8080/swagger-ui.html
```

**인증 방법:**
1. `POST /api/v1/auth/signup` 으로 회원가입
2. `POST /api/v1/auth/login` 으로 로그인 → `accessToken` 획득
3. 우측 상단 **Authorize** 버튼 클릭 → `accessToken` 값 입력

---

## API 엔드포인트 요약

### 인증 (인증 불필요)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/auth/signup` | 회원가입 |
| POST | `/api/v1/auth/login` | 로그인 (JWT 발급) |

### 대화 (JWT 필요)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/chats` | 대화 생성 (스트리밍 옵션 포함) |
| GET | `/api/v1/chats` | 대화 목록 조회 (스레드 그룹화) |
| DELETE | `/api/v1/threads/{threadId}` | 스레드 삭제 |

### 피드백 (JWT 필요)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/feedbacks` | 피드백 생성 |
| GET | `/api/v1/feedbacks` | 피드백 목록 조회 |
| PATCH | `/api/v1/feedbacks/{feedbackId}/status` | 피드백 상태 변경 (ADMIN) |

### 분석 (ADMIN JWT 필요)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/admin/analytics/activity` | 24시간 활동 통계 |
| GET | `/api/v1/admin/analytics/report` | 24시간 대화 CSV 다운로드 |

---

## 주요 기능 설명

### 스레드 기반 컨텍스트 유지

- 첫 질문 또는 마지막 질문으로부터 **30분 이상** 경과 시 → 새 스레드 생성
- 30분 이내 재질문 → 기존 스레드에 대화 추가
- OpenAI 요청 시 스레드 내 전체 대화 이력을 `messages` 배열에 포함

### 스트리밍 응답

```bash
curl -X POST http://localhost:8080/api/v1/chats \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"question": "안녕하세요!", "isStreaming": true}'
```

응답: `text/event-stream` (SSE)

### 역할 기반 접근 제어

| 기능 | MEMBER | ADMIN |
|------|--------|-------|
| 본인 대화 조회 | ✅ | ✅ |
| 전체 대화 조회 | ❌ | ✅ |
| 본인 스레드 삭제 | ✅ | ✅ |
| 타인 스레드 삭제 | ❌ | ✅ |
| 피드백 상태 변경 | ❌ | ✅ |
| 분석/보고서 조회 | ❌ | ✅ |

### ADMIN 계정 생성

회원가입 API는 기본적으로 `MEMBER` 역할을 부여합니다.
ADMIN 계정은 DB에서 직접 변경합니다:

```sql
-- H2 콘솔: http://localhost:8080/h2-console
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

---

## 프로젝트 구조

```
src/main/kotlin/com/example/chatbot/
├── common/
│   ├── config/          # SecurityConfig, SwaggerConfig, WebClientConfig
│   ├── exception/       # BusinessException 계층, GlobalExceptionHandler
│   ├── response/        # ApiResponse, ErrorResponse, PageResponse
│   └── security/        # JwtProvider, JwtAuthenticationFilter, SecurityUtil
├── domain/
│   ├── auth/            # 회원가입, 로그인, JWT
│   ├── chat/            # 대화 생성, 스레드 관리 (TIER 0-2)
│   ├── feedback/        # 피드백 CRUD
│   └── analytics/       # 활동 통계, CSV 보고서
└── infrastructure/
    └── openai/          # OpenAI WebClient 래퍼
```
