package com.example.chatbot.domain.chat.dto

import com.example.chatbot.domain.chat.entity.Chat
import com.example.chatbot.domain.chat.entity.Thread
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime
import java.util.UUID

data class CreateChatRequest(
    @field:NotBlank(message = "질문은 필수입니다.")
    @Schema(description = "질문", example = "안녕하세요!")
    val question: String,

    @Schema(description = "스트리밍 여부", example = "false", defaultValue = "false")
    val isStreaming: Boolean = false,

    @Schema(description = "사용할 모델 (미입력 시 기본 모델 사용)", example = "gpt-4o")
    val model: String? = null,
)

data class ChatResponse(
    @Schema(description = "대화 ID")
    val id: UUID,
    @Schema(description = "스레드 ID")
    val threadId: UUID,
    @Schema(description = "질문")
    val question: String,
    @Schema(description = "답변")
    val answer: String,
    @Schema(description = "사용된 모델")
    val model: String,
    @Schema(description = "생성일시")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(chat: Chat) = ChatResponse(
            id = chat.id,
            threadId = chat.thread.id,
            question = chat.question,
            answer = chat.answer,
            model = chat.model,
            createdAt = chat.createdAt,
        )
    }
}

data class ChatSummary(
    @Schema(description = "대화 ID")
    val id: UUID,
    @Schema(description = "질문")
    val question: String,
    @Schema(description = "답변")
    val answer: String,
    @Schema(description = "사용된 모델")
    val model: String,
    @Schema(description = "생성일시")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(chat: Chat) = ChatSummary(
            id = chat.id,
            question = chat.question,
            answer = chat.answer,
            model = chat.model,
            createdAt = chat.createdAt,
        )
    }
}

data class ThreadWithChatsResponse(
    @Schema(description = "스레드 ID")
    val threadId: UUID,
    @Schema(description = "스레드 생성일시")
    val createdAt: OffsetDateTime,
    @Schema(description = "대화 목록")
    val chats: List<ChatSummary>,
) {
    companion object {
        fun from(thread: Thread, chats: List<Chat>) = ThreadWithChatsResponse(
            threadId = thread.id,
            createdAt = thread.createdAt,
            chats = chats.map { ChatSummary.from(it) },
        )
    }
}
