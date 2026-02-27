package com.example.chatbot.domain.document.service

import com.example.chatbot.domain.document.dto.CreateDocumentRequest
import com.example.chatbot.domain.document.dto.DocumentResponse
import com.example.chatbot.domain.document.entity.KnowledgeDocument
import com.example.chatbot.domain.document.repository.KnowledgeDocumentRepository
import org.springframework.ai.document.Document
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DocumentService(
    private val vectorStore: VectorStore,
    private val knowledgeDocumentRepository: KnowledgeDocumentRepository,
) {
    @Transactional
    fun addDocument(request: CreateDocumentRequest): DocumentResponse {
        val sourceDoc = Document(request.content, mapOf("source" to request.name))
        val chunks = TokenTextSplitter().apply(listOf(sourceDoc))

        vectorStore.add(chunks)

        val doc = knowledgeDocumentRepository.save(
            KnowledgeDocument(
                name = request.name,
                contentPreview = request.content.take(500),
                chunksCount = chunks.size,
            )
        )

        return DocumentResponse.from(doc)
    }

    fun getDocuments(): List<DocumentResponse> =
        knowledgeDocumentRepository.findAll().map { DocumentResponse.from(it) }
}
