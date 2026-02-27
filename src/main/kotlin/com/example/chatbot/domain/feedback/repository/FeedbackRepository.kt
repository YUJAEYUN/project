package com.example.chatbot.domain.feedback.repository

import com.example.chatbot.domain.feedback.entity.Feedback
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface FeedbackRepository : JpaRepository<Feedback, UUID> {
    fun existsByUserIdAndChatId(userId: UUID, chatId: UUID): Boolean

    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Feedback>

    fun findAllByUserIdAndIsPositive(userId: UUID, isPositive: Boolean, pageable: Pageable): Page<Feedback>

    @Query("SELECT f FROM Feedback f WHERE (:isPositive IS NULL OR f.isPositive = :isPositive)")
    fun findAllByOptionalIsPositive(isPositive: Boolean?, pageable: Pageable): Page<Feedback>

    @Query("SELECT f FROM Feedback f WHERE f.user.id = :userId AND (:isPositive IS NULL OR f.isPositive = :isPositive)")
    fun findAllByUserIdAndOptionalIsPositive(userId: UUID, isPositive: Boolean?, pageable: Pageable): Page<Feedback>
}
