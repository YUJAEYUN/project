package com.example.chatbot.common.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID

@Component
class JwtProvider(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration-ms}") private val expirationMs: Long,
) {
    private val key by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    fun generateToken(userId: UUID, role: String): String {
        val now = Date()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .issuedAt(now)
            .expiration(Date(now.time + expirationMs))
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
            true
        } catch (e: JwtException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun getUserId(token: String): UUID {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        return UUID.fromString(claims.subject)
    }

    fun getRole(token: String): String {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        return claims["role"] as String
    }

    fun getExpirationMs(): Long = expirationMs
}
