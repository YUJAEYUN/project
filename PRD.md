# PRD — AI 챗봇 서비스

> **버전**: 1.0.0
> **작성일**: 2026-02-27
> **스택**: Kotlin 1.9.x · Spring Boot 3.x · PostgreSQL 15.8+ (개발: H2)

---

## 1. 배경 및 목적

VIP Onboarding 팀은 잠재 고객사를 대상으로 AI 챗봇 API를 시연해야 합니다.
고객사는 OpenAI 등 AI provider의 존재는 알고 있으나 API spec에 대한 깊은 이해는 없으며,
향후 자사 대외비 문서를 학습시킬 수 있는 확장 가능한 구조를 기대하고 있습니다.

**시연 목표**: "API를 통해 AI를 활용할 수 있다"는 것을 증명하는 최소 동작 가능 제품(MVP) 제공

---

## 2. 범위

| 영역 | 포함 여부 |
|------|-----------|
| 사용자 관리 & JWT 인증 | ✅ |
| AI 대화 생성 (OpenAI 연동) | ✅ |
| 스레드 기반 대화 컨텍스트 유지 | ✅ |
| 스트리밍(SSE) 응답 | ✅ |
| 피드백 관리 | ✅ |
| 관리자 분석 & CSV 보고서 | ✅ |
| 프론트엔드 UI | ❌ (API only) |
| RAG / 문서 학습 | ❌ (향후 확장 예정) |

---

## 3. 사용자 역할

| 역할 | 설명 |
|------|------|
| `MEMBER` | 일반 사용자. 본인 데이터만 접근 가능 |
| `ADMIN` | 관리자. 전체 데이터 조회·관리 가능 |

기본 가입 역할은 `MEMBER`이며, `ADMIN` 지정은 DB 직접 관리로 처리합니다.

---

## 4. 도메인 모델

### 4-1. User

| 컬럼 | 타입 | 제약 |
|------|------|------|
| id | UUID | PK |
| email | VARCHAR | UNIQUE, NOT NULL |
| password | VARCHAR | NOT NULL (BCrypt) |
| name | VARCHAR | NOT NULL |
| role | VARCHAR | NOT NULL, default: `MEMBER` |
| created_at | TIMESTAMPTZ | NOT NULL |

### 4-2. Thread

| 컬럼 | 타입 | 제약 |
|------|------|------|
| id | UUID | PK |
| user_id | UUID | FK → User, NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL |
| last_chat_at | TIMESTAMPTZ | NOT NULL |

### 4-3. Chat

| 컬럼 | 타입 | 제약 |
|------|------|------|
| id | UUID | PK |
| thread_id | UUID | FK → Thread, NOT NULL |
| user_id | UUID | FK → User, NOT NULL |
| question | TEXT | NOT NULL |
| answer | TEXT | NOT NULL |
| model | VARCHAR | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL |

### 4-4. Feedback

| 컬럼 | 타입 | 제약 |
|------|------|------|
| id | UUID | PK |
| user_id | UUID | FK → User, NOT NULL |
| chat_id | UUID | FK → Chat, NOT NULL |
| is_positive | BOOLEAN | NOT NULL |
| status | VARCHAR | NOT NULL, default: `PENDING` |
| created_at | TIMESTAMPTZ | NOT NULL |
| _(unique)_ | | UNIQUE(user_id, chat_id) |

### 4-5. ActivityLog _(분석용)_

| 컬럼 | 타입 | 제약 |
|------|------|------|
| id | UUID | PK |
| user_id | UUID | FK → User, NOT NULL |
| event_type | VARCHAR | NOT NULL (`SIGNUP` · `LOGIN` · `CHAT`) |
| created_at | TIMESTAMPTZ | NOT NULL |

---

## 5. API 명세

### 공통

- Base URL: `/api/v1`
- 인증: `Authorization: Bearer <JWT>` (회원가입·로그인 제외 전 엔드포인트)
- 응답 형식: `application/json`
- 공통 에러 구조:
  ```json
  { "code": "ERROR_CODE", "message": "설명" }
  ```

---

### 5-1. 인증 (Auth)

#### `POST /auth/signup` — 회원가입

**인증 불필요**

Request:
```json
{ "email": "user@example.com", "password": "secret123", "name": "홍길동" }
```

Response `201`:
```json
{ "id": "uuid", "email": "user@example.com", "name": "홍길동", "role": "MEMBER", "createdAt": "2026-02-27T00:00:00Z" }
```

| 에러 코드 | HTTP | 조건 |
|-----------|------|------|
| `EMAIL_DUPLICATED` | 409 | 이미 가입된 이메일 |
| `INVALID_REQUEST` | 400 | 필수 필드 누락·형식 오류 |

---

#### `POST /auth/login` — 로그인

**인증 불필요**

Request:
```json
{ "email": "user@example.com", "password": "secret123" }
```

Response `200`:
```json
{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600 }
```

| 에러 코드 | HTTP | 조건 |
|-----------|------|------|
| `INVALID_CREDENTIALS` | 401 | 이메일·패스워드 불일치 |

---

### 5-2. 대화 (Chat)

#### `POST /chats` — 대화 생성

**인증 필요** | 역할: `MEMBER`, `ADMIN`

Request:
```json
{
  "question": "안녕하세요!",
  "isStreaming": false,
  "model": "gpt-4o"
}
```

- `isStreaming`: 선택, default `false`
- `model`: 선택, default = 환경변수 `OPENAI_DEFAULT_MODEL`

Response `201` (non-streaming):
```json
{
  "id": "uuid",
  "threadId": "uuid",
  "question": "안녕하세요!",
  "answer": "안녕하세요! 무엇을 도와드릴까요?",
  "model": "gpt-4o",
  "createdAt": "2026-02-27T00:00:00Z"
}
```

Response (streaming): `Content-Type: text/event-stream`
```
data: {"delta": "안녕"}
data: {"delta": "하세요!"}
data: [DONE]
```

**스레드 결정 로직**:
```
현재 유저의 최신 스레드가 없음
  OR (현재시각 - lastChatAt) > 30분  →  새 스레드 생성
그 외                                →  기존 스레드 재사용
```

**OpenAI messages 구성**:
```json
[
  { "role": "system",    "content": "<system prompt>" },
  { "role": "user",      "content": "<이전 질문>" },
  { "role": "assistant", "content": "<이전 답변>" },
  "...(스레드 내 전체 이력)...",
  { "role": "user",      "content": "<현재 질문>" }
]
```

| 에러 코드 | HTTP | 조건 |
|-----------|------|------|
| `INVALID_REQUEST` | 400 | question 누락 |
| `OPENAI_ERROR` | 502 | OpenAI API 오류 |

---

#### `GET /chats` — 대화 목록 조회

**인증 필요** | 역할: `MEMBER`(본인), `ADMIN`(전체)

Query Parameters:

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| page | int | 0 | 페이지 번호 (0-based) |
| size | int | 20 | 페이지 크기 |
| sort | string | `desc` | `asc` \| `desc` (createdAt 기준) |
| userId | UUID | - | ADMIN 전용 유저 필터 |

Response `200`:
```json
{
  "content": [
    {
      "threadId": "uuid",
      "createdAt": "2026-02-27T00:00:00Z",
      "chats": [
        {
          "id": "uuid",
          "question": "안녕하세요!",
          "answer": "안녕하세요! 무엇을 도와드릴까요?",
          "model": "gpt-4o",
          "createdAt": "2026-02-27T00:00:00Z"
        }
      ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

> 페이지네이션 단위: **스레드** 기준
> 정렬 기준: 스레드의 `createdAt`

---

#### `DELETE /threads/{threadId}` — 스레드 삭제

**인증 필요** | 역할: `MEMBER`(본인 소유), `ADMIN`(전체)

Response `204`: No Content

| 에러 코드 | HTTP | 조건 |
|-----------|------|------|
| `THREAD_NOT_FOUND` | 404 | 존재하지 않는 스레드 |
| `FORBIDDEN` | 403 | 본인 소유 아닌 스레드 (MEMBER) |

> 스레드 삭제 시 연관 Chat 데이터 cascade 삭제

---

### 5-3. 피드백 (Feedback)

#### `POST /feedbacks` — 피드백 생성

**인증 필요** | 역할: `MEMBER`(본인 대화), `ADMIN`(전체 대화)

Request:
```json
{ "chatId": "uuid", "isPositive": true }
```

Response `201`:
```json
{
  "id": "uuid",
  "chatId": "uuid",
  "isPositive": true,
  "status": "PENDING",
  "createdAt": "2026-02-27T00:00:00Z"
}
```

| 에러 코드 | HTTP | 조건 |
|-----------|------|------|
| `CHAT_NOT_FOUND` | 404 | 존재하지 않는 대화 |
| `FORBIDDEN` | 403 | 본인 대화 아님 (MEMBER) |
| `FEEDBACK_DUPLICATED` | 409 | 동일 (userId, chatId) 피드백 존재 |

---

#### `GET /feedbacks` — 피드백 목록 조회

**인증 필요** | 역할: `MEMBER`(본인), `ADMIN`(전체)

Query Parameters:

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| page | int | 0 | 페이지 번호 |
| size | int | 20 | 페이지 크기 |
| sort | string | `desc` | `asc` \| `desc` (createdAt 기준) |
| isPositive | boolean | - | 긍정/부정 필터 (선택) |

Response `200`:
```json
{
  "content": [
    {
      "id": "uuid",
      "chatId": "uuid",
      "userId": "uuid",
      "isPositive": true,
      "status": "PENDING",
      "createdAt": "2026-02-27T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

#### `PATCH /feedbacks/{feedbackId}/status` — 피드백 상태 변경

**인증 필요** | 역할: `ADMIN` 전용

Request:
```json
{ "status": "RESOLVED" }
```

Response `200`:
```json
{ "id": "uuid", "status": "RESOLVED" }
```

| 에러 코드 | HTTP | 조건 |
|-----------|------|------|
| `FEEDBACK_NOT_FOUND` | 404 | 존재하지 않는 피드백 |
| `FORBIDDEN` | 403 | ADMIN 아닌 경우 |

---

### 5-4. 분석 & 보고 (Analytics)

#### `GET /admin/analytics/activity` — 사용자 활동 통계

**인증 필요** | 역할: `ADMIN` 전용

> 기준: 요청 시각으로부터 최근 24시간

Response `200`:
```json
{
  "signupCount": 10,
  "loginCount": 42,
  "chatCount": 137,
  "from": "2026-02-26T00:00:00Z",
  "to": "2026-02-27T00:00:00Z"
}
```

---

#### `GET /admin/analytics/report` — CSV 보고서 다운로드

**인증 필요** | 역할: `ADMIN` 전용

> 기준: 요청 시각으로부터 최근 24시간

Response `200`:
- `Content-Type: text/csv; charset=UTF-8`
- `Content-Disposition: attachment; filename="report_20260227.csv"`

CSV 컬럼:
```
chatId, threadId, userId, userEmail, userName, question, answer, model, createdAt
```

---

## 6. 보안 정책

| 항목 | 정책 |
|------|------|
| 패스워드 저장 | BCrypt (strength 10) |
| JWT 서명 | HS256, 만료 1시간 |
| JWT secret | 환경변수 `JWT_SECRET` |
| 토큰 검증 실패 | `401 Unauthorized` |
| 권한 부족 | `403 Forbidden` |
| ADMIN 지정 | DB 직접 관리 (API 미노출) |

---

## 7. 환경변수

| 키 | 설명 | 예시 |
|----|------|------|
| `OPENAI_API_KEY` | OpenAI API 키 | `sk-...` |
| `OPENAI_DEFAULT_MODEL` | 기본 사용 모델 | `gpt-4o` |
| `OPENAI_BASE_URL` | OpenAI base URL | `https://api.openai.com/v1` |
| `JWT_SECRET` | JWT 서명 시크릿 | `your-256-bit-secret` |
| `JWT_EXPIRATION_MS` | JWT 만료 시간 (ms) | `3600000` |
| `SPRING_PROFILES_ACTIVE` | 실행 프로파일 | `local` \| `prod` |
| `DB_URL` | PostgreSQL URL (prod) | `jdbc:postgresql://...` |
| `DB_USERNAME` | DB 사용자 | `postgres` |
| `DB_PASSWORD` | DB 패스워드 | `...` |

---

## 8. 프로파일 전략

| 프로파일 | DB | 용도 |
|----------|----|------|
| `local` | H2 in-memory | 로컬 개발·테스트 |
| `prod` | PostgreSQL 15.8+ | 운영·시연 |

---

## 9. 확장 고려사항

향후 아래 기능을 추가할 수 있도록 도메인 구조를 열어둡니다.

| 기능 | 방향 |
|------|------|
| RAG (문서 학습) | Thread에 `knowledgeBaseId` 연결, 임베딩 파이프라인 추가 |
| 멀티 Provider | `model` 필드 기반으로 OpenAI 외 provider 라우팅 |
| Refresh Token | `TokenStore` 테이블 추가, `/auth/refresh` 엔드포인트 |
| Rate Limiting | Spring Cloud Gateway 또는 Bucket4j 도입 |
| Soft Delete | Thread·Chat에 `deletedAt` 컬럼 추가 |

---

## 10. 구현 우선순위 & 일정 (3시간)

| 시간 | 작업 | 산출물 |
|------|------|--------|
| 0:00–0:30 | 프로젝트 셋업 | Gradle, Security 골격, 공통 응답·예외 |
| 0:30–1:00 | P1 인증 | `/auth/signup`, `/auth/login`, JWT 필터 |
| 1:00–1:45 | P2 대화 생성 | OpenAI 연동, 스레드 로직, SSE 스트리밍 |
| 1:45–2:15 | P3 목록·삭제 | 대화 목록 (스레드 그룹화·페이지네이션), 스레드 삭제 |
| 2:15–2:40 | P4 피드백 | 피드백 CRUD, 상태 변경 |
| 2:40–3:00 | P5 분석 + README | 활동 통계, CSV 보고서, 실행 가이드 |
