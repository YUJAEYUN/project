package com.example.chatbot.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {

    @Bean
    fun openAiWebClient(
        @Value("\${openai.base-url}") baseUrl: String,
        @Value("\${openai.api-key}") apiKey: String,
    ): WebClient = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Authorization", "Bearer $apiKey")
        .defaultHeader("Content-Type", "application/json")
        .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
        .build()
}
