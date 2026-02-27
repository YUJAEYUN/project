package com.example.chatbot.domain.document.repository

import com.example.chatbot.domain.document.entity.KnowledgeDocument
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KnowledgeDocumentRepository : JpaRepository<KnowledgeDocument, UUID>
