package com.example.chatbot.common.config

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.Filter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class VectorStoreConfig {

    @Bean
    @Profile("local", "test")
    fun vectorStore(): VectorStore = NoOpVectorStore()
}

/**
 * 로컬/테스트 환경용 No-Op VectorStore.
 * 실제 임베딩 API 호출 없이 앱이 동작하도록 한다.
 * 프로덕션 환경에서는 PgVectorStore(spring-ai-pgvector-store 자동 구성)가 사용된다.
 */
class NoOpVectorStore : VectorStore {

    override fun getName(): String = "NoOpVectorStore"

    override fun add(documents: List<Document>) {
        // no-op: 로컬에서는 문서를 벡터 DB에 저장하지 않음
    }

    override fun delete(idList: List<String>) {
        // no-op
    }

    override fun delete(filterExpression: Filter.Expression) {
        // no-op
    }

    override fun similaritySearch(request: SearchRequest): List<Document> = emptyList()
}
