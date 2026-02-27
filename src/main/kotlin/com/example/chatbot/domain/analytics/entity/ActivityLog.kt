package com.example.chatbot.domain.analytics.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.OffsetDateTime
import java.util.UUID

enum class EventType { SIGNUP, LOGIN, CHAT }

@Entity
@Table(name = "activity_logs")
class ActivityLog(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val eventType: EventType,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
