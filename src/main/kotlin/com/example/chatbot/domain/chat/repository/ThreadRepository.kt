package com.example.chatbot.domain.chat.repository

import com.example.chatbot.domain.chat.entity.Thread
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ThreadRepository : JpaRepository<Thread, UUID> {
    fun findTopByUserIdOrderByLastChatAtDesc(userId: UUID): Thread?

    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Thread>

    @Query("SELECT t FROM Thread t WHERE (:userId IS NULL OR t.user.id = :userId)")
    fun findAllByOptionalUserId(userId: UUID?, pageable: Pageable): Page<Thread>
}
