package com.example.chatbot.infrastructure.openai.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class OpenAiMessage(
    val role: String,
    val content: String,
)

data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = false,
)

data class OpenAiResponse(
    val id: String,
    val choices: List<Choice>,
) {
    data class Choice(
        val message: OpenAiMessage,
    )

    fun getContent(): String = choices.firstOrNull()?.message?.content ?: ""
}

data class OpenAiStreamChunk(
    val id: String,
    val choices: List<StreamChoice>,
) {
    data class StreamChoice(
        val delta: Delta,
        @JsonProperty("finish_reason") val finishReason: String?,
    )

    data class Delta(
        val content: String?,
    )

    fun getDeltaContent(): String? = choices.firstOrNull()?.delta?.content
    fun isDone(): Boolean = choices.firstOrNull()?.finishReason == "stop"
}
