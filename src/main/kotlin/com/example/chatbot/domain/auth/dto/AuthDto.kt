package com.example.chatbot.domain.auth.dto

import com.example.chatbot.domain.auth.entity.Role
import com.example.chatbot.domain.auth.entity.User
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime
import java.util.UUID

data class SignupRequest(
    @field:Email(message = "올바른 이메일 형식이 아닙니다.")
    @field:NotBlank(message = "이메일은 필수입니다.")
    @Schema(description = "이메일", example = "user@example.com")
    val email: String,

    @field:NotBlank(message = "패스워드는 필수입니다.")
    @Schema(description = "패스워드", example = "secret123")
    val password: String,

    @field:NotBlank(message = "이름은 필수입니다.")
    @Schema(description = "이름", example = "홍길동")
    val name: String,
)

data class LoginRequest(
    @field:NotBlank(message = "이메일은 필수입니다.")
    @Schema(description = "이메일", example = "user@example.com")
    val email: String,

    @field:NotBlank(message = "패스워드는 필수입니다.")
    @Schema(description = "패스워드", example = "secret123")
    val password: String,
)

data class UserResponse(
    @Schema(description = "사용자 ID")
    val id: UUID,
    @Schema(description = "이메일")
    val email: String,
    @Schema(description = "이름")
    val name: String,
    @Schema(description = "역할", example = "MEMBER")
    val role: Role,
    @Schema(description = "생성일시")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = user.id,
            email = user.email,
            name = user.name,
            role = user.role,
            createdAt = user.createdAt,
        )
    }
}

data class TokenResponse(
    @Schema(description = "액세스 토큰")
    val accessToken: String,
    @Schema(description = "토큰 타입", example = "Bearer")
    val tokenType: String = "Bearer",
    @Schema(description = "만료 시간(초)", example = "3600")
    val expiresIn: Long,
)
