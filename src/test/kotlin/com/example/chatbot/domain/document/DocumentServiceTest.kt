package com.example.chatbot.domain.document

import com.example.chatbot.domain.document.dto.CreateDocumentRequest
import com.example.chatbot.domain.document.entity.KnowledgeDocument
import com.example.chatbot.domain.document.repository.KnowledgeDocumentRepository
import com.example.chatbot.domain.document.service.DocumentService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.vectorstore.VectorStore

class DocumentServiceTest {

    private val vectorStore: VectorStore = mockk()
    private val knowledgeDocumentRepository: KnowledgeDocumentRepository = mockk()
    private val documentService = DocumentService(vectorStore, knowledgeDocumentRepository)

    @Test
    fun `정상 문서 업로드 - 청킹 후 벡터스토어에 저장`() {
        val content = "인공지능(AI)은 컴퓨터 과학의 한 분야로, 인간의 지능을 모방합니다. ".repeat(20)
        val request = CreateDocumentRequest(name = "AI 개요", content = content)

        every { vectorStore.add(any()) } just Runs
        every { knowledgeDocumentRepository.save(any()) } answers { firstArg() }

        val result = documentService.addDocument(request)

        assertThat(result.name).isEqualTo("AI 개요")
        assertThat(result.chunksCount).isGreaterThanOrEqualTo(1)
        assertThat(result.contentPreview).startsWith("인공지능")
        verify { vectorStore.add(any()) }
        verify { knowledgeDocumentRepository.save(any()) }
    }

    @Test
    fun `짧은 내용 문서 - 단일 청크로 저장`() {
        val request = CreateDocumentRequest(name = "짧은 문서", content = "짧은 내용입니다.")

        every { vectorStore.add(any()) } just Runs
        every { knowledgeDocumentRepository.save(any()) } answers { firstArg() }

        val result = documentService.addDocument(request)

        assertThat(result.name).isEqualTo("짧은 문서")
        assertThat(result.chunksCount).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `500자 초과 내용은 contentPreview가 500자로 잘림`() {
        val longContent = "A".repeat(600)
        val request = CreateDocumentRequest(name = "긴 문서", content = longContent)

        every { vectorStore.add(any()) } just Runs
        every { knowledgeDocumentRepository.save(any()) } answers { firstArg() }

        val result = documentService.addDocument(request)

        assertThat(result.contentPreview.length).isEqualTo(500)
    }

    @Test
    fun `문서 목록 조회 - 저장된 문서 반환`() {
        val doc1 = KnowledgeDocument(name = "문서1", contentPreview = "내용1", chunksCount = 2)
        val doc2 = KnowledgeDocument(name = "문서2", contentPreview = "내용2", chunksCount = 3)
        every { knowledgeDocumentRepository.findAll() } returns listOf(doc1, doc2)

        val result = documentService.getDocuments()

        assertThat(result).hasSize(2)
        assertThat(result[0].name).isEqualTo("문서1")
        assertThat(result[1].name).isEqualTo("문서2")
    }

    @Test
    fun `문서 목록 조회 - 저장된 문서 없으면 빈 리스트`() {
        every { knowledgeDocumentRepository.findAll() } returns emptyList()

        val result = documentService.getDocuments()

        assertThat(result).isEmpty()
    }
}
