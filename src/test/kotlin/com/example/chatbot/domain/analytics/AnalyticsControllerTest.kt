package com.example.chatbot.domain.analytics

import com.example.chatbot.common.config.SecurityConfig
import com.example.chatbot.common.config.SwaggerConfig
import com.example.chatbot.common.security.JwtProvider
import com.example.chatbot.domain.analytics.controller.AnalyticsController
import com.example.chatbot.domain.analytics.service.ActivityStatsResponse
import com.example.chatbot.domain.analytics.service.AnalyticsService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest(controllers = [AnalyticsController::class])
@Import(SecurityConfig::class, SwaggerConfig::class)
class AnalyticsControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var analyticsService: AnalyticsService

    @MockkBean
    lateinit var jwtProvider: JwtProvider

    private val adminId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()

    private fun adminAuth() = authentication(
        UsernamePasswordAuthenticationToken(adminId, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
    )

    private fun memberAuth() = authentication(
        UsernamePasswordAuthenticationToken(memberId, null, listOf(SimpleGrantedAuthority("ROLE_MEMBER")))
    )

    private val mockStats = ActivityStatsResponse(
        signupCount = 10,
        loginCount = 42,
        chatCount = 137,
        from = OffsetDateTime.now().minusHours(24),
        to = OffsetDateTime.now(),
    )

    // ─── GET /admin/analytics/activity ──────────────────────

    @Test
    fun `ADMIN 활동 통계 조회 성공 - 200`() {
        every { analyticsService.getActivityStats() } returns mockStats

        mockMvc.get("/api/v1/admin/analytics/activity") {
            with(adminAuth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.signupCount") { value(10) }
            jsonPath("$.loginCount") { value(42) }
            jsonPath("$.chatCount") { value(137) }
            jsonPath("$.from") { exists() }
            jsonPath("$.to") { exists() }
        }
    }

    @Test
    fun `MEMBER 활동 통계 접근 - 403`() {
        mockMvc.get("/api/v1/admin/analytics/activity") {
            with(memberAuth())
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `인증 없이 활동 통계 접근 - 401`() {
        mockMvc.get("/api/v1/admin/analytics/activity").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `응답에 from-to 24시간 범위 포함 확인`() {
        every { analyticsService.getActivityStats() } returns mockStats

        mockMvc.get("/api/v1/admin/analytics/activity") {
            with(adminAuth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.from") { exists() }
            jsonPath("$.to") { exists() }
        }
    }

    // ─── GET /admin/analytics/report ────────────────────────

    @Test
    fun `ADMIN CSV 보고서 다운로드 성공 - 200`() {
        val csvContent = "chatId,threadId,userId,userEmail,userName,question,answer,model,createdAt\n"
        every { analyticsService.generateCsvReport() } returns csvContent

        mockMvc.get("/api/v1/admin/analytics/report") {
            with(adminAuth())
        }.andExpect {
            status { isOk() }
            header { exists("Content-Disposition") }
        }
    }

    @Test
    fun `CSV 헤더 컬럼 확인`() {
        val csvContent = "chatId,threadId,userId,userEmail,userName,question,answer,model,createdAt\n" +
            "${UUID.randomUUID()},${UUID.randomUUID()},${UUID.randomUUID()},test@example.com,테스터,질문,답변,gpt-4o,2026-02-27T00:00:00Z\n"
        every { analyticsService.generateCsvReport() } returns csvContent

        val result = mockMvc.get("/api/v1/admin/analytics/report") {
            with(adminAuth())
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val body = result.response.contentAsString
        assert(body.contains("chatId"))
        assert(body.contains("userEmail"))
        assert(body.contains("question"))
    }

    @Test
    fun `MEMBER CSV 보고서 접근 - 403`() {
        mockMvc.get("/api/v1/admin/analytics/report") {
            with(memberAuth())
        }.andExpect {
            status { isForbidden() }
        }
    }
}
