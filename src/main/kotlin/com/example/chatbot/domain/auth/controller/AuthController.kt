package com.example.chatbot.domain.auth.controller

import com.example.chatbot.domain.auth.dto.LoginRequest
import com.example.chatbot.domain.auth.dto.SignupRequest
import com.example.chatbot.domain.auth.dto.TokenResponse
import com.example.chatbot.domain.auth.dto.UserResponse
import com.example.chatbot.domain.auth.service.AuthService
import com.example.chatbot.common.response.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {
    @Operation(summary = "회원가입", description = "이메일, 패스워드, 이름으로 회원을 생성합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "회원가입 성공"),
        ApiResponse(
            responseCode = "400", description = "요청 형식 오류",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "409", description = "이메일 중복",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
    )
    @SecurityRequirement(name = "")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@Valid @RequestBody request: SignupRequest): UserResponse {
        return authService.signup(request)
    }

    @Operation(summary = "로그인", description = "이메일, 패스워드로 로그인하고 JWT 토큰을 발급받습니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "로그인 성공"),
        ApiResponse(
            responseCode = "401", description = "이메일 또는 패스워드 불일치",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
    )
    @SecurityRequirement(name = "")
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse {
        return authService.login(request)
    }
}
