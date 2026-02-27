package com.example.chatbot.domain.auth

import com.example.chatbot.common.TestFixtures
import com.example.chatbot.common.exception.EmailDuplicatedException
import com.example.chatbot.common.exception.InvalidCredentialsException
import com.example.chatbot.common.security.JwtProvider
import com.example.chatbot.domain.auth.dto.LoginRequest
import com.example.chatbot.domain.auth.dto.SignupRequest
import com.example.chatbot.domain.auth.dto.TokenResponse
import com.example.chatbot.domain.auth.dto.UserResponse
import com.example.chatbot.domain.auth.service.AuthService
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import com.example.chatbot.common.config.SecurityConfig
import com.example.chatbot.common.config.SwaggerConfig

@WebMvcTest(controllers = [com.example.chatbot.domain.auth.controller.AuthController::class])
@Import(SecurityConfig::class, SwaggerConfig::class)
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockkBean
    lateinit var authService: AuthService

    @MockkBean
    lateinit var jwtProvider: JwtProvider

    @Test
    fun `정상 회원가입 성공 - 201`() {
        val user = TestFixtures.createUser()
        val request = SignupRequest("user@example.com", "password123", "홍길동")
        every { authService.signup(any()) } returns UserResponse.from(user)

        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.email") { value("user@example.com") }
            jsonPath("$.name") { value("홍길동") }
            jsonPath("$.role") { value("MEMBER") }
        }
    }

    @Test
    fun `이메일 중복 - 409`() {
        every { authService.signup(any()) } throws EmailDuplicatedException()

        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                SignupRequest("dup@example.com", "password123", "홍길동")
            )
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EMAIL_DUPLICATED") }
        }
    }

    @Test
    fun `이메일 형식 오류 - 400`() {
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                SignupRequest("not-an-email", "password123", "홍길동")
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `패스워드 누락 - 400`() {
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@example.com","name":"홍길동"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `이름 누락 - 400`() {
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@example.com","password":"password123"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `정상 로그인 성공 - JWT 발급 - 200`() {
        every { authService.login(any()) } returns TokenResponse(
            accessToken = "mocked.jwt.token",
            expiresIn = 3600L,
        )

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest("user@example.com", "password123")
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { value("mocked.jwt.token") }
            jsonPath("$.tokenType") { value("Bearer") }
            jsonPath("$.expiresIn") { value(3600) }
        }
    }

    @Test
    fun `이메일 불일치 - 401`() {
        every { authService.login(any()) } throws InvalidCredentialsException()

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest("notfound@example.com", "password123")
            )
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `패스워드 불일치 - 401`() {
        every { authService.login(any()) } throws InvalidCredentialsException()

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest("user@example.com", "wrongpassword")
            )
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `토큰 없이 인증 필요 API 접근 - 401`() {
        // 인증이 필요한 아무 엔드포인트 (이 테스트는 Security 레이어 확인)
        every { jwtProvider.validateToken(any()) } returns false

        mockMvc.post("/api/v1/chats") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"question":"test"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
