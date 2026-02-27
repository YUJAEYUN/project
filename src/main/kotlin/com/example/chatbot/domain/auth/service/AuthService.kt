package com.example.chatbot.domain.auth.service

import com.example.chatbot.common.exception.EmailDuplicatedException
import com.example.chatbot.common.exception.InvalidCredentialsException
import com.example.chatbot.common.security.JwtProvider
import com.example.chatbot.domain.analytics.entity.ActivityLog
import com.example.chatbot.domain.analytics.entity.EventType
import com.example.chatbot.domain.analytics.repository.ActivityLogRepository
import com.example.chatbot.domain.auth.dto.LoginRequest
import com.example.chatbot.domain.auth.dto.SignupRequest
import com.example.chatbot.domain.auth.dto.TokenResponse
import com.example.chatbot.domain.auth.dto.UserResponse
import com.example.chatbot.domain.auth.entity.User
import com.example.chatbot.domain.auth.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val activityLogRepository: ActivityLogRepository,
) {
    @Transactional
    fun signup(request: SignupRequest): UserResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw EmailDuplicatedException()
        }
        val user = User(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            name = request.name,
        )
        val saved = userRepository.save(user)
        activityLogRepository.save(ActivityLog(userId = saved.id, eventType = EventType.SIGNUP))
        return UserResponse.from(saved)
    }

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw InvalidCredentialsException()
        }

        activityLogRepository.save(ActivityLog(userId = user.id, eventType = EventType.LOGIN))
        val token = jwtProvider.generateToken(user.id, user.role.name)
        return TokenResponse(
            accessToken = token,
            expiresIn = jwtProvider.getExpirationMs() / 1000,
        )
    }
}
