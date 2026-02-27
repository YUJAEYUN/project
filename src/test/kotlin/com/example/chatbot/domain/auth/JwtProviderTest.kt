package com.example.chatbot.domain.auth

import com.example.chatbot.common.security.JwtProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class JwtProviderTest {

    private val secret = "test-secret-key-must-be-at-least-256-bits-long-so-adding-more-chars"
    private val expirationMs = 3600000L
    private val jwtProvider = JwtProvider(secret, expirationMs)

    @Test
    fun `토큰 생성 후 파싱 성공`() {
        val userId = UUID.randomUUID()
        val role = "MEMBER"

        val token = jwtProvider.generateToken(userId, role)

        assertThat(jwtProvider.validateToken(token)).isTrue()
        assertThat(jwtProvider.getUserId(token)).isEqualTo(userId)
        assertThat(jwtProvider.getRole(token)).isEqualTo(role)
    }

    @Test
    fun `만료된 토큰 검증 실패`() {
        val expiredProvider = JwtProvider(secret, -1L)
        val token = expiredProvider.generateToken(UUID.randomUUID(), "MEMBER")

        assertThat(jwtProvider.validateToken(token)).isFalse()
    }

    @Test
    fun `변조된 토큰 검증 실패`() {
        val token = jwtProvider.generateToken(UUID.randomUUID(), "MEMBER")
        val tamperedToken = token.dropLast(5) + "XXXXX"

        assertThat(jwtProvider.validateToken(tamperedToken)).isFalse()
    }

    @Test
    fun `잘못된 형식의 토큰 검증 실패`() {
        assertThat(jwtProvider.validateToken("not.a.valid.token")).isFalse()
    }
}
