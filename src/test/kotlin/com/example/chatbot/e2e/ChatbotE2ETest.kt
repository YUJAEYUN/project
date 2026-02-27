package com.example.chatbot.e2e

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatbotE2ETest {

    companion object {
        val mockWebServer = MockWebServer().also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun overrideOpenAiBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("openai.base-url") { "http://localhost:${mockWebServer.port}" }
        }

        @AfterAll
        @JvmStatic
        fun tearDownAll() {
            mockWebServer.shutdown()
        }
    }

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun url(path: String) = "http://localhost:$port$path"

    private fun enqueueOpenAiResponse(content: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"chatcmpl-test","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":"$content"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}"""
                )
                .addHeader("Content-Type", "application/json")
        )
    }

    private fun jsonHeaders(token: String? = null): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        if (token != null) headers.setBearerAuth(token)
        return headers
    }

    @Suppress("UNCHECKED_CAST")
    private fun signup(email: String, name: String, password: String = "Pass1234!"): Map<String, Any> {
        val resp = restTemplate.postForEntity(
            url("/api/v1/auth/signup"),
            HttpEntity("""{"email":"$email","name":"$name","password":"$password"}""", jsonHeaders()),
            Map::class.java,
        )
        assertThat(resp.statusCode).isEqualTo(HttpStatus.CREATED)
        return resp.body as Map<String, Any>
    }

    private fun login(email: String, password: String = "Pass1234!"): String {
        val resp = restTemplate.postForEntity(
            url("/api/v1/auth/login"),
            HttpEntity("""{"email":"$email","password":"$password"}""", jsonHeaders()),
            Map::class.java,
        )
        assertThat(resp.statusCode).isEqualTo(HttpStatus.OK)
        return resp.body!!["accessToken"] as String
    }

    private fun promoteToAdmin(email: String) {
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email)
    }

    // ─── Test 1: MEMBER 기본 플로우 ────────────────────────────────────────────

    @Test
    fun `MEMBER 전체 플로우 - 채팅 및 스레드 재사용, 피드백 생성 및 조회`() {
        val email = "member-${UUID.randomUUID()}@test.com"
        val signupResp = signup(email, "테스트유저")
        assertThat(signupResp["email"]).isEqualTo(email)
        assertThat(signupResp["role"]).isEqualTo("MEMBER")

        val token = login(email)
        assertThat(token).isNotBlank()

        // 첫 번째 채팅
        enqueueOpenAiResponse("안녕하세요! 무엇을 도와드릴까요?")
        val chat1Resp = restTemplate.postForEntity(
            url("/api/v1/chats"),
            HttpEntity("""{"question":"안녕하세요","isStreaming":false}""", jsonHeaders(token)),
            Map::class.java,
        )
        assertThat(chat1Resp.statusCode).isEqualTo(HttpStatus.OK)
        val chat1 = chat1Resp.body!!
        val chat1Id = chat1["id"] as String
        val threadId = chat1["threadId"] as String
        assertThat(chat1["answer"]).isEqualTo("안녕하세요! 무엇을 도와드릴까요?")

        // 두 번째 채팅 - 같은 스레드 재사용 (30분 이내이므로)
        enqueueOpenAiResponse("Spring Boot는 Java/Kotlin 기반 웹 프레임워크입니다.")
        val chat2Resp = restTemplate.postForEntity(
            url("/api/v1/chats"),
            HttpEntity("""{"question":"Spring Boot가 뭔가요?","isStreaming":false}""", jsonHeaders(token)),
            Map::class.java,
        )
        assertThat(chat2Resp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(chat2Resp.body!!["threadId"]).isEqualTo(threadId) // 스레드 재사용 확인

        // 스레드(대화 목록) 조회
        val threadsResp = restTemplate.exchange(
            url("/api/v1/chats"),
            HttpMethod.GET,
            HttpEntity<Void>(jsonHeaders(token)),
            Map::class.java,
        )
        assertThat(threadsResp.statusCode).isEqualTo(HttpStatus.OK)
        @Suppress("UNCHECKED_CAST")
        val threads = threadsResp.body!!["content"] as List<*>
        assertThat(threads).isNotEmpty()
        assertThat(threadsResp.body!!["totalElements"] as Int).isGreaterThanOrEqualTo(1)

        // 피드백 생성
        val feedbackResp = restTemplate.postForEntity(
            url("/api/v1/feedbacks"),
            HttpEntity("""{"chatId":"$chat1Id","isPositive":true}""", jsonHeaders(token)),
            Map::class.java,
        )
        assertThat(feedbackResp.statusCode).isEqualTo(HttpStatus.CREATED)
        val feedback = feedbackResp.body!!
        assertThat(feedback["chatId"]).isEqualTo(chat1Id)
        assertThat(feedback["isPositive"]).isEqualTo(true)
        assertThat(feedback["status"]).isEqualTo("PENDING")

        // 피드백 목록 조회
        val feedbacksResp = restTemplate.exchange(
            url("/api/v1/feedbacks"),
            HttpMethod.GET,
            HttpEntity<Void>(jsonHeaders(token)),
            Map::class.java,
        )
        assertThat(feedbacksResp.statusCode).isEqualTo(HttpStatus.OK)
        @Suppress("UNCHECKED_CAST")
        val feedbackContent = feedbacksResp.body!!["content"] as List<*>
        assertThat(feedbackContent).isNotEmpty()
    }

    // ─── Test 2: 스레드 삭제 ─────────────────────────────────────────────────

    @Test
    fun `MEMBER 스레드 삭제 - 채팅 후 삭제 성공 204`() {
        val email = "member-del-${UUID.randomUUID()}@test.com"
        signup(email, "삭제테스트유저")
        val token = login(email)

        enqueueOpenAiResponse("테스트 응답입니다.")
        val chatResp = restTemplate.postForEntity(
            url("/api/v1/chats"),
            HttpEntity("""{"question":"삭제 테스트용 질문","isStreaming":false}""", jsonHeaders(token)),
            Map::class.java,
        )
        assertThat(chatResp.statusCode).isEqualTo(HttpStatus.OK)
        val threadId = chatResp.body!!["threadId"] as String

        val deleteResp = restTemplate.exchange(
            url("/api/v1/threads/$threadId"),
            HttpMethod.DELETE,
            HttpEntity<Void>(jsonHeaders(token)),
            Void::class.java,
        )
        assertThat(deleteResp.statusCode).isEqualTo(HttpStatus.NO_CONTENT)

        // 삭제 후 목록에서 없어짐 확인
        val threadsAfter = restTemplate.exchange(
            url("/api/v1/chats"),
            HttpMethod.GET,
            HttpEntity<Void>(jsonHeaders(token)),
            Map::class.java,
        )
        @Suppress("UNCHECKED_CAST")
        val contentAfter = threadsAfter.body!!["content"] as List<*>
        val threadIds = contentAfter.map { (it as Map<*, *>)["threadId"] }
        assertThat(threadIds).doesNotContain(threadId)
    }

    // ─── Test 3: ADMIN 전체 플로우 ────────────────────────────────────────────

    @Test
    fun `ADMIN 전체 플로우 - 분석 통계, CSV 보고서, RAG 문서 관리, 피드백 상태 변경`() {
        // ADMIN 계정 준비
        val adminEmail = "admin-${UUID.randomUUID()}@test.com"
        signup(adminEmail, "어드민유저")
        promoteToAdmin(adminEmail)
        val adminToken = login(adminEmail)

        // MEMBER 계정 생성 및 채팅/피드백 준비
        val memberEmail = "member-for-admin-${UUID.randomUUID()}@test.com"
        signup(memberEmail, "멤버유저")
        val memberToken = login(memberEmail)

        enqueueOpenAiResponse("RAG는 검색 증강 생성(Retrieval-Augmented Generation)입니다.")
        val chatResp = restTemplate.postForEntity(
            url("/api/v1/chats"),
            HttpEntity("""{"question":"RAG가 무엇인가요?","isStreaming":false}""", jsonHeaders(memberToken)),
            Map::class.java,
        )
        assertThat(chatResp.statusCode).isEqualTo(HttpStatus.OK)
        val chatId = chatResp.body!!["id"] as String

        val feedbackResp = restTemplate.postForEntity(
            url("/api/v1/feedbacks"),
            HttpEntity("""{"chatId":"$chatId","isPositive":false}""", jsonHeaders(memberToken)),
            Map::class.java,
        )
        assertThat(feedbackResp.statusCode).isEqualTo(HttpStatus.CREATED)
        val feedbackId = feedbackResp.body!!["id"] as String

        // 활동 통계 조회
        val statsResp = restTemplate.exchange(
            url("/api/v1/admin/analytics/activity"),
            HttpMethod.GET,
            HttpEntity<Void>(jsonHeaders(adminToken)),
            Map::class.java,
        )
        assertThat(statsResp.statusCode).isEqualTo(HttpStatus.OK)
        val stats = statsResp.body!!
        assertThat(stats.keys).contains("signupCount", "loginCount", "chatCount", "from", "to")
        assertThat(stats["chatCount"] as Int).isGreaterThanOrEqualTo(1)

        // CSV 보고서 다운로드
        val reportResp = restTemplate.exchange(
            url("/api/v1/admin/analytics/report"),
            HttpMethod.GET,
            HttpEntity<Void>(jsonHeaders(adminToken)),
            ByteArray::class.java,
        )
        assertThat(reportResp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(reportResp.headers.getFirst("Content-Disposition")).contains("attachment")
        val csvContent = String(reportResp.body!!, Charsets.UTF_8)
        assertThat(csvContent).contains("chatId,threadId,userId")

        // RAG 문서 업로드
        val docResp = restTemplate.postForEntity(
            url("/api/v1/documents"),
            HttpEntity(
                """{"name":"Spring AI 소개","content":"Spring AI는 AI 기능을 Spring 애플리케이션에 쉽게 통합할 수 있는 프레임워크입니다."}""",
                jsonHeaders(adminToken),
            ),
            Map::class.java,
        )
        assertThat(docResp.statusCode).isEqualTo(HttpStatus.CREATED)
        @Suppress("UNCHECKED_CAST")
        val docData = docResp.body!!["data"] as Map<String, Any>
        assertThat(docData["name"]).isEqualTo("Spring AI 소개")
        assertThat(docData["chunksCount"] as Int).isGreaterThanOrEqualTo(1)
        assertThat(docData["contentPreview"] as String).isNotBlank()

        // RAG 문서 목록 조회
        val docsListResp = restTemplate.exchange(
            url("/api/v1/documents"),
            HttpMethod.GET,
            HttpEntity<Void>(jsonHeaders(adminToken)),
            Map::class.java,
        )
        assertThat(docsListResp.statusCode).isEqualTo(HttpStatus.OK)
        @Suppress("UNCHECKED_CAST")
        val docsList = docsListResp.body!!["data"] as List<*>
        assertThat(docsList).isNotEmpty()

        // 피드백 상태 변경 (PENDING → RESOLVED)
        val statusResp = restTemplate.exchange(
            url("/api/v1/feedbacks/$feedbackId/status"),
            HttpMethod.PATCH,
            HttpEntity("""{"status":"RESOLVED"}""", jsonHeaders(adminToken)),
            Map::class.java,
        )
        assertThat(statusResp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(statusResp.body!!["id"]).isEqualTo(feedbackId)
        assertThat(statusResp.body!!["status"]).isEqualTo("RESOLVED")
    }

    // ─── Test 4: 보안 - 미인증 접근 401 ───────────────────────────────────────

    @Test
    fun `보안 - 인증 없이 보호 엔드포인트 접근 시 401`() {
        val noAuth = HttpEntity<Void>(jsonHeaders())

        assertThat(
            restTemplate.exchange(url("/api/v1/chats"), HttpMethod.GET, noAuth, Map::class.java).statusCode
        ).isEqualTo(HttpStatus.UNAUTHORIZED)

        assertThat(
            restTemplate.exchange(url("/api/v1/feedbacks"), HttpMethod.GET, noAuth, Map::class.java).statusCode
        ).isEqualTo(HttpStatus.UNAUTHORIZED)

        assertThat(
            restTemplate.exchange(url("/api/v1/documents"), HttpMethod.GET, noAuth, Map::class.java).statusCode
        ).isEqualTo(HttpStatus.UNAUTHORIZED)

        assertThat(
            restTemplate.exchange(url("/api/v1/admin/analytics/activity"), HttpMethod.GET, noAuth, Map::class.java).statusCode
        ).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    // ─── Test 5: 보안 - MEMBER의 ADMIN 엔드포인트 접근 403 ───────────────────

    @Test
    fun `보안 - MEMBER가 ADMIN 전용 엔드포인트 접근 시 403`() {
        val email = "member-403-${UUID.randomUUID()}@test.com"
        signup(email, "멤버403유저")
        val token = login(email)

        assertThat(
            restTemplate.exchange(
                url("/api/v1/documents"), HttpMethod.GET,
                HttpEntity<Void>(jsonHeaders(token)), Map::class.java
            ).statusCode
        ).isEqualTo(HttpStatus.FORBIDDEN)

        assertThat(
            restTemplate.exchange(
                url("/api/v1/admin/analytics/activity"), HttpMethod.GET,
                HttpEntity<Void>(jsonHeaders(token)), Map::class.java
            ).statusCode
        ).isEqualTo(HttpStatus.FORBIDDEN)

        assertThat(
            restTemplate.postForEntity(
                url("/api/v1/documents"),
                HttpEntity("""{"name":"문서","content":"내용"}""", jsonHeaders(token)),
                Map::class.java
            ).statusCode
        ).isEqualTo(HttpStatus.FORBIDDEN)
    }

    // ─── Test 6: 비즈니스 로직 - 이메일 중복 가입 409 ──────────────────────

    @Test
    fun `비즈니스 로직 - 이메일 중복 가입 시 409 반환`() {
        val email = "dup-${UUID.randomUUID()}@test.com"
        signup(email, "첫번째유저")

        val dupResp = restTemplate.postForEntity(
            url("/api/v1/auth/signup"),
            HttpEntity("""{"email":"$email","name":"두번째유저","password":"Pass1234!"}""", jsonHeaders()),
            Map::class.java,
        )
        assertThat(dupResp.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    // ─── Test 7: 비즈니스 로직 - 잘못된 비밀번호 로그인 401 ────────────────

    @Test
    fun `비즈니스 로직 - 잘못된 비밀번호로 로그인 시 401 반환`() {
        val email = "wrong-pw-${UUID.randomUUID()}@test.com"
        signup(email, "비밀번호테스트유저")

        val resp = restTemplate.postForEntity(
            url("/api/v1/auth/login"),
            HttpEntity("""{"email":"$email","password":"WrongPassword!"}""", jsonHeaders()),
            Map::class.java,
        )
        assertThat(resp.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }
}
