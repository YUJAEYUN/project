package com.example.chatbot.domain.auth

import com.example.chatbot.common.TestFixtures
import com.example.chatbot.common.exception.EmailDuplicatedException
import com.example.chatbot.common.exception.InvalidCredentialsException
import com.example.chatbot.common.security.JwtProvider
import com.example.chatbot.domain.analytics.repository.ActivityLogRepository
import com.example.chatbot.domain.auth.dto.LoginRequest
import com.example.chatbot.domain.auth.dto.SignupRequest
import com.example.chatbot.domain.auth.repository.UserRepository
import com.example.chatbot.domain.auth.service.AuthService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AuthServiceTest {

    private val userRepository: UserRepository = mockk()
    private val passwordEncoder = BCryptPasswordEncoder()
    private val jwtProvider: JwtProvider = mockk()
    private val activityLogRepository: ActivityLogRepository = mockk()
    private val authService = AuthService(userRepository, passwordEncoder, jwtProvider, activityLogRepository)

    @Test
    fun `정상 회원가입 성공`() {
        val request = SignupRequest("user@example.com", "password123", "홍길동")
        every { userRepository.existsByEmail(any()) } returns false
        every { userRepository.save(any()) } answers { firstArg() }
        every { activityLogRepository.save(any()) } answers { firstArg() }

        val result = authService.signup(request)

        assertThat(result.email).isEqualTo("user@example.com")
        assertThat(result.name).isEqualTo("홍길동")
        verify { userRepository.save(any()) }
    }

    @Test
    fun `이메일 중복 시 EmailDuplicatedException 발생`() {
        val request = SignupRequest("dup@example.com", "password123", "홍길동")
        every { userRepository.existsByEmail("dup@example.com") } returns true

        assertThrows<EmailDuplicatedException> { authService.signup(request) }
    }

    @Test
    fun `정상 로그인 성공 - JWT 발급`() {
        val rawPassword = "password123"
        val user = TestFixtures.createUser(
            email = "user@example.com",
            password = passwordEncoder.encode(rawPassword),
        )
        val request = LoginRequest("user@example.com", rawPassword)
        every { userRepository.findByEmail("user@example.com") } returns user
        every { jwtProvider.generateToken(user.id, "MEMBER") } returns "mocked.jwt.token"
        every { jwtProvider.getExpirationMs() } returns 3600000L
        every { activityLogRepository.save(any()) } answers { firstArg() }

        val result = authService.login(request)

        assertThat(result.accessToken).isEqualTo("mocked.jwt.token")
        assertThat(result.tokenType).isEqualTo("Bearer")
        assertThat(result.expiresIn).isEqualTo(3600L)
    }

    @Test
    fun `존재하지 않는 이메일로 로그인 시 InvalidCredentialsException`() {
        every { userRepository.findByEmail(any()) } returns null

        assertThrows<InvalidCredentialsException> {
            authService.login(LoginRequest("notfound@example.com", "password"))
        }
    }

    @Test
    fun `패스워드 불일치 시 InvalidCredentialsException`() {
        val user = TestFixtures.createUser(password = passwordEncoder.encode("correctPassword"))
        every { userRepository.findByEmail(any()) } returns user

        assertThrows<InvalidCredentialsException> {
            authService.login(LoginRequest("user@example.com", "wrongPassword"))
        }
    }
}
