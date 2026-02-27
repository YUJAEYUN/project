package com.example.chatbot.common.config

import com.example.chatbot.common.response.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.example.chatbot.common.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val PERMIT_ALL_PATHS = arrayOf(
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/h2-console/**",
        )
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { it.frameOptions { frame -> frame.disable() } }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(*PERMIT_ALL_PATHS).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { handling ->
                handling.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpStatus.UNAUTHORIZED.value()
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.characterEncoding = "UTF-8"
                    response.writer.write(
                        objectMapper.writeValueAsString(
                            ErrorResponse("UNAUTHORIZED", "인증이 필요합니다.")
                        )
                    )
                }
                handling.accessDeniedHandler { _, response, _ ->
                    response.status = HttpStatus.FORBIDDEN.value()
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.characterEncoding = "UTF-8"
                    response.writer.write(
                        objectMapper.writeValueAsString(
                            ErrorResponse("FORBIDDEN", "접근 권한이 없습니다.")
                        )
                    )
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
