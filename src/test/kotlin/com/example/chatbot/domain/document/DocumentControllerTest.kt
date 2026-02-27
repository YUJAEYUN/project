package com.example.chatbot.domain.document

import com.example.chatbot.common.config.SecurityConfig
import com.example.chatbot.common.config.SwaggerConfig
import com.example.chatbot.common.security.JwtProvider
import com.example.chatbot.domain.document.controller.DocumentController
import com.example.chatbot.domain.document.dto.DocumentResponse
import com.example.chatbot.domain.document.service.DocumentService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
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
import org.springframework.test.web.servlet.post
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest(controllers = [DocumentController::class])
@Import(SecurityConfig::class, SwaggerConfig::class)
class DocumentControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var documentService: DocumentService

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

    private val sampleResponse = DocumentResponse(
        id = UUID.randomUUID(),
        name = "테스트 문서",
        contentPreview = "테스트 내용 미리보기",
        chunksCount = 3,
        createdAt = OffsetDateTime.now(),
    )

    // ─── POST /api/v1/documents ───────────────────────────────

    @Test
    fun `ADMIN 문서 업로드 성공 - 201`() {
        every { documentService.addDocument(any()) } returns sampleResponse

        mockMvc.post("/api/v1/documents") {
            with(adminAuth())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "테스트 문서", "content": "문서 내용입니다."}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.name") { value("테스트 문서") }
            jsonPath("$.data.chunksCount") { value(3) }
        }
    }

    @Test
    fun `MEMBER 문서 업로드 시 403`() {
        mockMvc.post("/api/v1/documents") {
            with(memberAuth())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "문서", "content": "내용"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `인증 없이 문서 업로드 시 401`() {
        mockMvc.post("/api/v1/documents") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "문서", "content": "내용"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `빈 name으로 요청 시 400`() {
        mockMvc.post("/api/v1/documents") {
            with(adminAuth())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "", "content": "내용입니다."}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `빈 content로 요청 시 400`() {
        mockMvc.post("/api/v1/documents") {
            with(adminAuth())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "문서명", "content": ""}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ─── GET /api/v1/documents ────────────────────────────────

    @Test
    fun `ADMIN 문서 목록 조회 성공 - 200`() {
        every { documentService.getDocuments() } returns listOf(sampleResponse)

        mockMvc.get("/api/v1/documents") {
            with(adminAuth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].name") { value("테스트 문서") }
        }
    }

    @Test
    fun `MEMBER 문서 목록 조회 시 403`() {
        mockMvc.get("/api/v1/documents") {
            with(memberAuth())
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `인증 없이 문서 목록 조회 시 401`() {
        mockMvc.get("/api/v1/documents").andExpect {
            status { isUnauthorized() }
        }
    }
}
