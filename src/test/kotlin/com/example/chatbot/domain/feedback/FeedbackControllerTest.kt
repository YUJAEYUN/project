package com.example.chatbot.domain.feedback

import com.example.chatbot.common.config.SecurityConfig
import com.example.chatbot.common.config.SwaggerConfig
import com.example.chatbot.common.exception.ChatNotFoundException
import com.example.chatbot.common.exception.FeedbackDuplicatedException
import com.example.chatbot.common.exception.FeedbackNotFoundException
import com.example.chatbot.common.exception.ForbiddenException
import com.example.chatbot.common.response.PageResponse
import com.example.chatbot.common.security.JwtProvider
import com.example.chatbot.domain.feedback.controller.FeedbackController
import com.example.chatbot.domain.feedback.dto.CreateFeedbackRequest
import com.example.chatbot.domain.feedback.dto.FeedbackResponse
import com.example.chatbot.domain.feedback.dto.FeedbackStatusResponse
import com.example.chatbot.domain.feedback.dto.UpdateFeedbackStatusRequest
import com.example.chatbot.domain.feedback.entity.FeedbackStatus
import com.example.chatbot.domain.feedback.service.FeedbackService
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest(controllers = [FeedbackController::class])
@Import(SecurityConfig::class, SwaggerConfig::class)
class FeedbackControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockkBean
    lateinit var feedbackService: FeedbackService

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

    private fun mockFeedbackResponse(isPositive: Boolean = true) = FeedbackResponse(
        id = UUID.randomUUID(),
        chatId = UUID.randomUUID(),
        userId = userId,
        isPositive = isPositive,
        status = FeedbackStatus.PENDING,
        createdAt = OffsetDateTime.now(),
    )

    // ─── POST /api/v1/feedbacks ──────────────────────────────

    @Test
    fun `본인 대화 피드백 생성 성공 - 201`() {
        every { feedbackService.createFeedback(any(), any(), any()) } returns mockFeedbackResponse()

        mockMvc.post("/api/v1/feedbacks") {
            with(memberAuth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateFeedbackRequest(UUID.randomUUID(), true))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.isPositive") { value(true) }
            jsonPath("$.status") { value("PENDING") }
        }
    }

    @Test
    fun `ADMIN 타인 대화 피드백 생성 성공 - 201`() {
        every { feedbackService.createFeedback(any(), any(), any()) } returns mockFeedbackResponse()

        mockMvc.post("/api/v1/feedbacks") {
            with(adminAuth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateFeedbackRequest(UUID.randomUUID(), false))
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `MEMBER 타인 대화 피드백 - 403`() {
        every { feedbackService.createFeedback(any(), any(), any()) } throws ForbiddenException()

        mockMvc.post("/api/v1/feedbacks") {
            with(memberAuth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateFeedbackRequest(UUID.randomUUID(), true))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `존재하지 않는 chatId - 404`() {
        every { feedbackService.createFeedback(any(), any(), any()) } throws ChatNotFoundException()

        mockMvc.post("/api/v1/feedbacks") {
            with(memberAuth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateFeedbackRequest(UUID.randomUUID(), true))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CHAT_NOT_FOUND") }
        }
    }

    @Test
    fun `동일 대화 중복 피드백 - 409`() {
        every { feedbackService.createFeedback(any(), any(), any()) } throws FeedbackDuplicatedException()

        mockMvc.post("/api/v1/feedbacks") {
            with(memberAuth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateFeedbackRequest(UUID.randomUUID(), true))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("FEEDBACK_DUPLICATED") }
        }
    }

    @Test
    fun `인증 없음 - 401`() {
        mockMvc.post("/api/v1/feedbacks") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"chatId":"${UUID.randomUUID()}","isPositive":true}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    // ─── GET /api/v1/feedbacks ───────────────────────────────

    @Test
    fun `MEMBER 본인 피드백 목록 조회`() {
        val pageResponse = PageResponse(
            content = listOf(mockFeedbackResponse(true)),
            page = 0, size = 20, totalElements = 1, totalPages = 1,
        )
        every { feedbackService.getFeedbacks(any(), any(), any(), any(), any(), any()) } returns pageResponse

        mockMvc.get("/api/v1/feedbacks") {
            with(memberAuth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.content[0].isPositive") { value(true) }
            jsonPath("$.totalElements") { value(1) }
        }
    }

    @Test
    fun `isPositive true 필터`() {
        val pageResponse = PageResponse(
            content = listOf(mockFeedbackResponse(true)),
            page = 0, size = 20, totalElements = 1, totalPages = 1,
        )
        every { feedbackService.getFeedbacks(any(), any(), eq(true), any(), any(), any()) } returns pageResponse

        mockMvc.get("/api/v1/feedbacks?isPositive=true") {
            with(memberAuth())
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `isPositive false 필터`() {
        val pageResponse = PageResponse(
            content = listOf(mockFeedbackResponse(false)),
            page = 0, size = 20, totalElements = 1, totalPages = 1,
        )
        every { feedbackService.getFeedbacks(any(), any(), eq(false), any(), any(), any()) } returns pageResponse

        mockMvc.get("/api/v1/feedbacks?isPositive=false") {
            with(memberAuth())
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `페이지네이션 동작`() {
        val pageResponse = PageResponse<FeedbackResponse>(
            content = emptyList(), page = 1, size = 5, totalElements = 0, totalPages = 0
        )
        every { feedbackService.getFeedbacks(any(), any(), any(), eq(1), eq(5), any()) } returns pageResponse

        mockMvc.get("/api/v1/feedbacks?page=1&size=5") {
            with(memberAuth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.page") { value(1) }
        }
    }

    // ─── PATCH /api/v1/feedbacks/{id}/status ────────────────

    @Test
    fun `ADMIN 피드백 상태 변경 성공 - 200`() {
        val feedbackId = UUID.randomUUID()
        every { feedbackService.updateStatus(feedbackId, any()) } returns
            FeedbackStatusResponse(feedbackId, FeedbackStatus.RESOLVED)

        mockMvc.patch("/api/v1/feedbacks/$feedbackId/status") {
            with(adminAuth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(UpdateFeedbackStatusRequest(FeedbackStatus.RESOLVED))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("RESOLVED") }
        }
    }

    @Test
    fun `MEMBER 상태 변경 시도 - 403`() {
        val feedbackId = UUID.randomUUID()

        mockMvc.patch("/api/v1/feedbacks/$feedbackId/status") {
            with(memberAuth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(UpdateFeedbackStatusRequest(FeedbackStatus.RESOLVED))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `존재하지 않는 feedbackId - 404`() {
        val feedbackId = UUID.randomUUID()
        every { feedbackService.updateStatus(feedbackId, any()) } throws FeedbackNotFoundException()

        mockMvc.patch("/api/v1/feedbacks/$feedbackId/status") {
            with(adminAuth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(UpdateFeedbackStatusRequest(FeedbackStatus.RESOLVED))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("FEEDBACK_NOT_FOUND") }
        }
    }
}
