package com.example.chatbot.domain.feedback.dto

import com.example.chatbot.domain.feedback.entity.Feedback
import com.example.chatbot.domain.feedback.entity.FeedbackStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.UUID

data class CreateFeedbackRequest(
    @field:NotNull(message = "chatId는 필수입니다.")
    @Schema(description = "대화 ID")
    val chatId: UUID,

    @field:NotNull(message = "isPositive는 필수입니다.")
    @Schema(description = "긍정(true) / 부정(false)")
    val isPositive: Boolean,
)

data class UpdateFeedbackStatusRequest(
    @field:NotNull(message = "status는 필수입니다.")
    @Schema(description = "변경할 상태", example = "RESOLVED")
    val status: FeedbackStatus,
)

data class FeedbackResponse(
    @Schema(description = "피드백 ID")
    val id: UUID,
    @Schema(description = "대화 ID")
    val chatId: UUID,
    @Schema(description = "사용자 ID")
    val userId: UUID,
    @Schema(description = "긍정 여부")
    val isPositive: Boolean,
    @Schema(description = "상태")
    val status: FeedbackStatus,
    @Schema(description = "생성일시")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(feedback: Feedback) = FeedbackResponse(
            id = feedback.id,
            chatId = feedback.chat.id,
            userId = feedback.user.id,
            isPositive = feedback.isPositive,
            status = feedback.status,
            createdAt = feedback.createdAt,
        )
    }
}

data class FeedbackStatusResponse(
    @Schema(description = "피드백 ID")
    val id: UUID,
    @Schema(description = "변경된 상태")
    val status: FeedbackStatus,
)
