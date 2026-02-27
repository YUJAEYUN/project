package com.example.chatbot.domain.analytics.service

import com.example.chatbot.domain.analytics.entity.EventType
import com.example.chatbot.domain.analytics.repository.ActivityLogRepository
import com.example.chatbot.domain.auth.repository.UserRepository
import com.example.chatbot.domain.chat.repository.ChatRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

data class ActivityStatsResponse(
    val signupCount: Long,
    val loginCount: Long,
    val chatCount: Long,
    val from: OffsetDateTime,
    val to: OffsetDateTime,
)

data class ChatReportRow(
    val chatId: String,
    val threadId: String,
    val userId: String,
    val userEmail: String,
    val userName: String,
    val question: String,
    val answer: String,
    val model: String,
    val createdAt: String,
)

@Service
@Transactional(readOnly = true)
class AnalyticsService(
    private val activityLogRepository: ActivityLogRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
) {
    fun getActivityStats(): ActivityStatsResponse {
        val to = OffsetDateTime.now()
        val from = to.minusHours(24)
        return ActivityStatsResponse(
            signupCount = activityLogRepository.countByEventTypeAndCreatedAtBetween(EventType.SIGNUP, from, to),
            loginCount = activityLogRepository.countByEventTypeAndCreatedAtBetween(EventType.LOGIN, from, to),
            chatCount = activityLogRepository.countByEventTypeAndCreatedAtBetween(EventType.CHAT, from, to),
            from = from,
            to = to,
        )
    }

    fun generateCsvReport(): String {
        val to = OffsetDateTime.now()
        val from = to.minusHours(24)

        val chats = chatRepository.findAll().filter {
            it.createdAt.isAfter(from) && it.createdAt.isBefore(to)
        }

        val sb = StringBuilder()
        sb.appendLine("chatId,threadId,userId,userEmail,userName,question,answer,model,createdAt")

        chats.forEach { chat ->
            val row = listOf(
                chat.id,
                chat.thread.id,
                chat.user.id,
                chat.user.email,
                chat.user.name,
                escapeCsv(chat.question),
                escapeCsv(chat.answer),
                chat.model,
                chat.createdAt,
            ).joinToString(",")
            sb.appendLine(row)
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
