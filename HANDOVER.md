# AI Chatbot API — 클라이언트 전달 문서

> **전달일**: 2026-02-27
> **버전**: 1.0.0 (MVP)
> **스택**: Kotlin 1.9 · Spring Boot 3.2 · H2 (로컬) / PostgreSQL 15.8+ (운영)

---

## 1. 전달 범위 요약

본 전달물은 **AI 챗봇 REST API 서버** 전체 소스 코드 및 실행 환경 구성 파일입니다.

| 항목 | 내용 |
|------|------|
| 언어 | Kotlin 1.9.22 |
| 프레임워크 | Spring Boot 3.2.3 |
| AI | OpenAI API (GPT-4o 기본) |
| 인증 | JWT (HS256, 기본 만료 1시간) |
| DB | H2 in-memory (로컬) / PostgreSQL 15.8+ (운영) |
| API 문서 | Swagger UI 내장 |

---

## 2. 빠른 시작 (로컬 실행)

### 2-1. 사전 준비

- **JDK 21** 이상 설치 ([다운로드](https://adoptium.net))
- **OpenAI API Key** 발급 ([platform.openai.com](https://platform.openai.com))

### 2-2. 환경변수 설정

터미널에서 아래 두 가지를 반드시 설정해야 합니다.

```bash
export OPENAI_API_KEY=sk-...          # OpenAI API 키 (필수)
export JWT_SECRET=your-256-bit-secret # 256자 이상의 임의 문자열 (필수)
```

전체 환경변수 목록:

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `OPENAI_API_KEY` | OpenAI API 키 | **(필수)** |
| `JWT_SECRET` | JWT 서명 키 | **(필수)** |
| `OPENAI_DEFAULT_MODEL` | 기본 AI 모델 | `gpt-4o` |
| `OPENAI_BASE_URL` | OpenAI API 주소 | `https://api.openai.com/v1` |
| `JWT_EXPIRATION_MS` | 토큰 만료 시간(ms) | `3600000` (1시간) |

### 2-3. 서버 실행

```bash
# 프로젝트 루트 디렉토리에서 실행
./gradlew bootRun
```

서버가 기동되면 아래 주소에서 Swagger UI로 API를 확인할 수 있습니다.

```
http://localhost:8080/swagger-ui.html
```

### 2-4. 운영 환경 실행 (PostgreSQL)

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://localhost:5432/chatbot
export DB_USERNAME=postgres
export DB_PASSWORD=your-password

./gradlew bootRun
```

---

## 3. 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 결과 리포트
open build/reports/tests/test/index.html
```

---

## 4. API 사용 방법 (Swagger 기준)

### Step 1 — 회원가입

`POST /api/v1/auth/signup`

```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "홍길동"
}
```

### Step 2 — 로그인 & 토큰 발급

`POST /api/v1/auth/login`

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

응답의 `accessToken` 값을 복사합니다.

### Step 3 — Swagger 인증

Swagger UI 우측 상단 **Authorize** 버튼 클릭 → `accessToken` 값을 입력합니다.

### Step 4 — 챗봇 API 사용

`POST /api/v1/chats`

```json
{
  "question": "안녕하세요! 무엇이 궁금하신가요?",
  "model": "gpt-4o"
}
```

응답 예시:

```json
{
  "id": "uuid",
  "threadId": "uuid",
  "question": "안녕하세요!",
  "answer": "안녕하세요! 무엇을 도와드릴까요?",
  "model": "gpt-4o",
  "createdAt": "2026-02-27T14:00:00+09:00"
}
```

---

## 5. 구현 완료 기능 목록

### ✅ 인증 (Auth)

| API | 설명 |
|-----|------|
| `POST /api/v1/auth/signup` | 회원가입 (MEMBER 역할 부여) |
| `POST /api/v1/auth/login` | 로그인 및 JWT 발급 |

### ✅ 대화 (Chat)

| API | 설명 |
|-----|------|
| `POST /api/v1/chats` | AI 대화 생성 (JSON 응답) |
| `POST /api/v1/chats/stream` | AI 대화 생성 (SSE 스트리밍 응답) |
| `GET /api/v1/chats` | 대화 목록 조회 (스레드 그룹화 + 페이지네이션) |
| `DELETE /api/v1/threads/{threadId}` | 스레드 및 하위 대화 삭제 |

### ✅ 피드백 (Feedback)

| API | 설명 |
|-----|------|
| `POST /api/v1/feedbacks` | 대화에 피드백 생성 (👍 / 👎) |
| `GET /api/v1/feedbacks` | 피드백 목록 조회 |
| `PATCH /api/v1/feedbacks/{feedbackId}/status` | 피드백 상태 변경 (ADMIN 전용) |

### ✅ 관리자 분석 (Analytics)

| API | 설명 |
|-----|------|
| `GET /api/v1/admin/analytics/activity` | 최근 24시간 활동 통계 |
| `GET /api/v1/admin/analytics/report` | 최근 24시간 대화 CSV 다운로드 |

---

## 6. 주요 비즈니스 로직

### 스레드 기반 대화 컨텍스트

- 마지막 대화로부터 **30분 이내** 재질문 → 기존 스레드에 이어서 대화
- **30분 초과** 경과 → 새 스레드 생성 (새 대화 세션 시작)
- OpenAI 요청 시 **스레드 내 전체 이력**을 messages에 포함하여 문맥 유지

### 역할 기반 접근 제어 (RBAC)

| 기능 | MEMBER | ADMIN |
|------|:------:|:-----:|
| 본인 대화 조회 | ✅ | ✅ |
| 전체 대화 조회 | ❌ | ✅ |
| 본인 스레드 삭제 | ✅ | ✅ |
| 타인 스레드 삭제 | ❌ | ✅ |
| 피드백 상태 변경 | ❌ | ✅ |
| 분석 / 보고서 | ❌ | ✅ |

### ADMIN 계정 설정

회원가입은 기본적으로 `MEMBER` 역할로 생성됩니다.  
ADMIN 권한 부여는 DB에서 직접 처리합니다:

```sql
-- 로컬(H2 콘솔): http://localhost:8080/h2-console
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

---

## 7. 향후 확장 계획 (미구현)

현재 MVP에는 포함되지 않았지만, 인프라 구조는 아래 확장이 가능하도록 설계되어 있습니다.

| 기능 | 확장 방향 |
|------|----------|
| **사내 문서 학습 (RAG)** | Spring AI + pgvector 연동으로 문서 임베딩 및 유사도 검색 추가 |
| 멀티 AI Provider | `model` 필드 기반 라우팅으로 OpenAI 외 provider 확장 |
| Refresh Token | `TokenStore` 테이블 + `/auth/refresh` 엔드포인트 추가 |
| Rate Limiting | Bucket4j 또는 Spring Cloud Gateway 도입 |

> 💡 **RAG 확장에 대해**: Spring AI와 pgvector가 이미 의존성에 포함되어 있어, VectorStore 설정과 문서 임베딩 파이프라인 추가만으로 연동 가능합니다.

---

## 8. 프로젝트 구조

```
src/main/kotlin/com/example/chatbot/
├── common/
│   ├── config/          # SecurityConfig, SwaggerConfig, WebClientConfig
│   ├── exception/       # 예외 계층, GlobalExceptionHandler
│   ├── response/        # ApiResponse, ErrorResponse, PageResponse
│   └── security/        # JwtProvider, JwtAuthenticationFilter, SecurityUtil
├── domain/
│   ├── auth/            # 회원가입, 로그인, JWT
│   ├── chat/            # 대화 생성, 스레드 관리
│   ├── feedback/        # 피드백 CRUD
│   └── analytics/       # 활동 통계, CSV 보고서
└── infrastructure/
    └── openai/          # OpenAI WebClient 래퍼
```

---

*문의사항이 있으시면 언제든지 연락 주세요.*
