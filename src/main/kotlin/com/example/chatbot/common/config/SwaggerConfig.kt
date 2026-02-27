package com.example.chatbot.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("AI Chatbot API")
                .description(
                    """
                    VIP Onboarding 챗봇 서비스 API 명세

                    ## 인증
                    - 회원가입·로그인을 제외한 모든 요청에 JWT 토큰이 필요합니다.
                    - 로그인 후 발급된 `accessToken`을 우측 상단 **Authorize** 버튼에 입력하세요.
                    """.trimIndent()
                )
                .version("v1.0.0")
        )
        .addSecurityItem(SecurityRequirement().addList("BearerAuth"))
        .components(
            Components().addSecuritySchemes(
                "BearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("로그인 후 발급된 JWT 토큰을 입력하세요.")
            )
        )
}
