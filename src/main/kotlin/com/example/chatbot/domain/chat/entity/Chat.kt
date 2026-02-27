package com.example.chatbot.domain.chat.entity

import com.example.chatbot.domain.auth.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "chats")
class Chat(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    val thread: Thread,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, columnDefinition = "TEXT")
    val question: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val answer: String,

    @Column(nullable = false)
    val model: String,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
