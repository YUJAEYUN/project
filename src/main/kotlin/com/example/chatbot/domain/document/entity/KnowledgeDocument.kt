package com.example.chatbot.domain.document.entity

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "knowledge_documents")
class KnowledgeDocument(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val name: String,

    @Column(name = "content_preview", nullable = false, length = 500)
    val contentPreview: String,

    @Column(name = "chunks_count", nullable = false)
    val chunksCount: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
