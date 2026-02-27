package com.example.chatbot.domain.analytics.repository

import com.example.chatbot.domain.analytics.entity.ActivityLog
import com.example.chatbot.domain.analytics.entity.EventType
import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime
import java.util.UUID

interface ActivityLogRepository : JpaRepository<ActivityLog, UUID> {
    fun countByEventTypeAndCreatedAtBetween(
        eventType: EventType,
        from: OffsetDateTime,
        to: OffsetDateTime,
    ): Long
}
