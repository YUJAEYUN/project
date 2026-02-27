package com.example.chatbot.infrastructure.openai

import com.example.chatbot.common.exception.OpenAiException
import com.example.chatbot.infrastructure.openai.dto.OpenAiMessage
import com.example.chatbot.infrastructure.openai.dto.OpenAiRequest
import com.example.chatbot.infrastructure.openai.dto.OpenAiResponse
import com.example.chatbot.infrastructure.openai.dto.OpenAiStreamChunk
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux

@Component
class OpenAiClient(
    private val openAiWebClient: WebClient,
) {
    fun complete(model: String, messages: List<OpenAiMessage>): String {
        return try {
            openAiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(OpenAiRequest(model = model, messages = messages, stream = false))
                .retrieve()
                .bodyToMono(OpenAiResponse::class.java)
                .block()
                ?.getContent()
                ?: throw OpenAiException("OpenAI 응답이 비어있습니다.")
        } catch (e: WebClientResponseException) {
            throw OpenAiException("OpenAI API 오류: ${e.statusCode} - ${e.responseBodyAsString}")
        } catch (e: OpenAiException) {
            throw e
        } catch (e: Exception) {
            throw OpenAiException("OpenAI 연결 오류: ${e.message}")
        }
    }

    fun stream(model: String, messages: List<OpenAiMessage>): Flux<String> {
        return openAiWebClient.post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(OpenAiRequest(model = model, messages = messages, stream = true))
            .retrieve()
            .bodyToFlux(String::class.java)
            .filter { it.startsWith("data: ") }
            .map { it.removePrefix("data: ").trim() }
            .filter { it.isNotEmpty() && it != "[DONE]" }
            .onErrorMap { e ->
                if (e is OpenAiException) e
                else OpenAiException("OpenAI 스트리밍 오류: ${e.message}")
            }
    }
}
