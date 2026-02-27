package com.example.chatbot.domain.document.dto

import com.example.chatbot.domain.document.entity.KnowledgeDocument
import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime
import java.util.UUID

data class CreateDocumentRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val content: String,
)

data class DocumentResponse(
    val id: UUID,
    val name: String,
    val contentPreview: String,
    val chunksCount: Int,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(doc: KnowledgeDocument) = DocumentResponse(
            id = doc.id,
            name = doc.name,
            contentPreview = doc.contentPreview,
            chunksCount = doc.chunksCount,
            createdAt = doc.createdAt,
        )
    }
}
