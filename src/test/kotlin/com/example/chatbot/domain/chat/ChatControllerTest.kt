package com.example.chatbot.domain.chat

import com.example.chatbot.common.TestFixtures
import com.example.chatbot.common.config.SecurityConfig
import com.example.chatbot.common.config.SwaggerConfig
import com.example.chatbot.common.exception.ForbiddenException
import com.example.chatbot.common.exception.OpenAiException
import com.example.chatbot.common.exception.ThreadNotFoundException
import com.example.chatbot.common.response.PageResponse
import com.example.chatbot.common.security.JwtProvider
import com.example.chatbot.domain.chat.controller.ChatController
import com.example.chatbot.domain.chat.dto.ChatResponse
import com.example.chatbot.domain.chat.dto.ThreadWithChatsResponse
import com.example.chatbot.domain.chat.service.ChatService
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest(controllers = [ChatController::class])
@Import(SecurityConfig::class, SwaggerConfig::class)
class ChatControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockkBean
    lateinit var chatService: ChatService

    @MockkBean
    lateinit var jwtProvider: JwtProvider

    private val userId = UUID.randomUUID()
    private val adminId = UUID.randomUUID()

    private fun memberAuth() = authentication(
        UsernamePasswordAuthenticationToken(userId, null, listOf(SimpleGrantedAuthority("ROLE_MEMBER")))
    )

    private fun adminAuth() = authentication(
        UsernamePasswordAuthenticationToken(adminId, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
    )

    private fun mockChatResponse(threadId: UUID = UUID.randomUUID()) = ChatResponse(
        id = UUID.randomUUID(),
        threadId = threadId,
        question = "안녕하세요!",
        answer = "안녕하세요! 무엇을 도와드릴까요?",
        model = "gpt-4o",
        createdAt = OffsetDateTime.now(),
    )

    // ─── POST /api/v1/chats ────────────────────────────────────

    @Test
    fun `정상 non-streaming 대화 생성 - 200`() {
        every { chatService.createChat(any(), any()) } returns mockChatResponse()

        mockMvc.post("/api/v1/chats") {
            with(memberAuth())
            contentType = MediaType.APPLICATION_JSON
            content = """{"question":"안녕하세요!","isStreaming":false}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.question") { value("안녕하세요!") }
            jsonPath("$.answer") { value("안녕하세요! 무엇을 도와드릴까요?") }
        }
    }

    @Test
    fun `question 누락 - 400`() {
        mockMvc.post("/api/v1/chats") {
            with(memberAuth())
            contentType = MediaType.APPLICATION_JSON
            content = """{"isStreaming":false}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `인증 없음 - 401`() {
        mockMvc.post("/api/v1/chats") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"question":"test"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `OpenAI 오류 - 502`() {
        every { chatService.createChat(any(), any()) } throws OpenAiException("API 오류")

        mockMvc.post("/api/v1/chats") {
            with(memberAuth())
            contentType = MediaType.APPLICATION_JSON
            content = """{"question":"질문","isStreaming":false}"""
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value("OPENAI_ERROR") }
        }
    }

    @Test
    fun `커스텀 model 파라미터 적용`() {
        every { chatService.createChat(any(), any()) } returns mockChatResponse()

        mockMvc.post("/api/v1/chats") {
            with(memberAuth())
            contentType = MediaType.APPLICATION_JSON
            content = """{"question":"질문","model":"gpt-4-turbo","isStreaming":false}"""
        }.andExpect {
            status { isOk() }
        }
    }

    // ─── GET /api/v1/chats ─────────────────────────────────────

    @Test
    fun `MEMBER 본인 스레드 목록 조회`() {
        val pageResponse = PageResponse(
            content = listOf(
                ThreadWithChatsResponse(UUID.randomUUID(), OffsetDateTime.now(), emptyList())
            ),
            page = 0, size = 20, totalElements = 1, totalPages = 1,
        )
        every { chatService.getThreads(any(), any(), any(), any(), any(), any()) } returns pageResponse

        mockMvc.get("/api/v1/chats") {
            with(memberAuth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { isArray() }
            jsonPath("$.totalElements") { value(1) }
        }
    }

    @Test
    fun `페이지네이션 파라미터 동작`() {
        val pageResponse = PageResponse<ThreadWithChatsResponse>(
            content = emptyList(), page = 1, size = 5, totalElements = 0, totalPages = 0
        )
        every { chatService.getThreads(any(), any(), any(), eq(1), eq(5), any()) } returns pageResponse

        mockMvc.get("/api/v1/chats?page=1&size=5") {
            with(memberAuth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.page") { value(1) }
            jsonPath("$.size") { value(5) }
        }
    }

    @Test
    fun `GET chats 인증 없음 - 401`() {
        mockMvc.get("/api/v1/chats").andExpect {
            status { isUnauthorized() }
        }
    }

    // ─── DELETE /api/v1/threads/{threadId} ────────────────────

    @Test
    fun `본인 스레드 삭제 - 204`() {
        val threadId = UUID.randomUUID()
        every { chatService.deleteThread(any(), any(), threadId) } just Runs

        mockMvc.delete("/api/v1/threads/$threadId") {
            with(memberAuth())
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `타인 스레드 삭제 시도 MEMBER - 403`() {
        val threadId = UUID.randomUUID()
        every { chatService.deleteThread(any(), any(), threadId) } throws ForbiddenException()

        mockMvc.delete("/api/v1/threads/$threadId") {
            with(memberAuth())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `ADMIN 타인 스레드 삭제 - 204`() {
        val threadId = UUID.randomUUID()
        every { chatService.deleteThread(any(), any(), threadId) } just Runs

        mockMvc.delete("/api/v1/threads/$threadId") {
            with(adminAuth())
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `존재하지 않는 스레드 삭제 - 404`() {
        val threadId = UUID.randomUUID()
        every { chatService.deleteThread(any(), any(), threadId) } throws ThreadNotFoundException()

        mockMvc.delete("/api/v1/threads/$threadId") {
            with(memberAuth())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("THREAD_NOT_FOUND") }
        }
    }

    @Test
    fun `DELETE thread 인증 없음 - 401`() {
        mockMvc.delete("/api/v1/threads/${UUID.randomUUID()}").andExpect {
            status { isUnauthorized() }
        }
    }
}
