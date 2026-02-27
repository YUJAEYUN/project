package com.example.chatbot.domain.feedback.entity

import com.example.chatbot.domain.auth.entity.User
import com.example.chatbot.domain.chat.entity.Chat
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.OffsetDateTime
import java.util.UUID

enum class FeedbackStatus { PENDING, RESOLVED }

@Entity
@Table(
    name = "feedbacks",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "chat_id"])]
)
class Feedback(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    val chat: Chat,

    @Column(nullable = false)
    val isPositive: Boolean,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: FeedbackStatus = FeedbackStatus.PENDING,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
