package com.example.chatbot.domain.chat.repository

import com.example.chatbot.domain.chat.entity.Chat
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChatRepository : JpaRepository<Chat, UUID> {
    fun findAllByThreadIdOrderByCreatedAtAsc(threadId: UUID): List<Chat>
    fun findAllByThreadIdIn(threadIds: List<UUID>): List<Chat>
}
