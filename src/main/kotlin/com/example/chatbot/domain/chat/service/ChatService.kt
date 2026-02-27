package com.example.chatbot.domain.chat.service

import com.example.chatbot.common.exception.ForbiddenException
import com.example.chatbot.common.exception.ThreadNotFoundException
import com.example.chatbot.common.response.PageResponse
import com.example.chatbot.domain.analytics.entity.ActivityLog
import com.example.chatbot.domain.analytics.entity.EventType
import com.example.chatbot.domain.analytics.repository.ActivityLogRepository
import com.example.chatbot.domain.auth.entity.Role
import com.example.chatbot.domain.auth.repository.UserRepository
import com.example.chatbot.domain.chat.dto.ChatResponse
import com.example.chatbot.domain.chat.dto.CreateChatRequest
import com.example.chatbot.domain.chat.dto.ThreadWithChatsResponse
import com.example.chatbot.domain.chat.entity.Chat
import com.example.chatbot.domain.chat.entity.Thread
import com.example.chatbot.domain.chat.repository.ChatRepository
import com.example.chatbot.domain.chat.repository.ThreadRepository
import com.example.chatbot.infrastructure.openai.OpenAiClient
import com.example.chatbot.infrastructure.openai.dto.OpenAiMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val THREAD_TIMEOUT_MINUTES = 30L
private const val SYSTEM_PROMPT = "You are a helpful AI assistant. Respond in the same language as the user's message."

@Service
@Transactional(readOnly = true)
class ChatService(
    private val threadRepository: ThreadRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val openAiClient: OpenAiClient,
    private val activityLogRepository: ActivityLogRepository,
    @Value("\${openai.default-model}") private val defaultModel: String,
) {
    @Transactional
    fun createChat(userId: UUID, request: CreateChatRequest): ChatResponse {
        val user = userRepository.findById(userId).orElseThrow()
        val thread = resolveThread(user.id)
        val model = request.model ?: defaultModel
        val history = chatRepository.findAllByThreadIdOrderByCreatedAtAsc(thread.id)
        val messages = buildMessages(history, request.question)

        val answer = openAiClient.complete(model, messages)

        val chat = chatRepository.save(
            Chat(
                thread = thread,
                user = user,
                question = request.question,
                answer = answer,
                model = model,
            )
        )
        thread.lastChatAt = chat.createdAt
        activityLogRepository.save(ActivityLog(userId = userId, eventType = EventType.CHAT))
        return ChatResponse.from(chat)
    }

    fun createChatStream(userId: UUID, request: CreateChatRequest): Flux<String> {
        val user = userRepository.findById(userId).orElseThrow()
        val model = request.model ?: defaultModel

        // 스레드 결정 및 히스토리 조회 (트랜잭션 밖에서 수행 후 스트림 종료 시 저장)
        val thread = resolveThread(user.id)
        val history = chatRepository.findAllByThreadIdOrderByCreatedAtAsc(thread.id)
        val messages = buildMessages(history, request.question)

        val contentBuffer = StringBuilder()

        return openAiClient.stream(model, messages)
            .doOnNext { chunk -> contentBuffer.append(chunk) }
            .doOnComplete {
                saveStreamedChat(thread, user.id, request.question, contentBuffer.toString(), model)
            }
    }

    @Transactional
    fun getThreads(
        requestUserId: UUID,
        requestUserRole: Role,
        filterUserId: UUID?,
        page: Int,
        size: Int,
        sort: String,
    ): PageResponse<ThreadWithChatsResponse> {
        val direction = if (sort == "asc") Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt"))

        val targetUserId = when {
            requestUserRole == Role.ADMIN -> filterUserId
            else -> requestUserId
        }

        val threadPage = threadRepository.findAllByOptionalUserId(targetUserId, pageable)
        val threadIds = threadPage.content.map { it.id }
        val chatMap = if (threadIds.isNotEmpty()) {
            chatRepository.findAllByThreadIdIn(threadIds)
                .groupBy { it.thread.id }
        } else {
            emptyMap()
        }

        val content = threadPage.content.map { thread ->
            ThreadWithChatsResponse.from(thread, chatMap[thread.id] ?: emptyList())
        }

        return PageResponse(
            content = content,
            page = threadPage.number,
            size = threadPage.size,
            totalElements = threadPage.totalElements,
            totalPages = threadPage.totalPages,
        )
    }

    @Transactional
    fun deleteThread(requestUserId: UUID, requestUserRole: Role, threadId: UUID) {
        val thread = threadRepository.findById(threadId)
            .orElseThrow { ThreadNotFoundException() }

        if (requestUserRole != Role.ADMIN && thread.user.id != requestUserId) {
            throw ForbiddenException()
        }

        threadRepository.delete(thread)
    }

    private fun resolveThread(userId: UUID): Thread {
        val latest = threadRepository.findTopByUserIdOrderByLastChatAtDesc(userId)
        val user = userRepository.findById(userId).orElseThrow()

        return if (latest == null ||
            ChronoUnit.MINUTES.between(latest.lastChatAt, OffsetDateTime.now()) >= THREAD_TIMEOUT_MINUTES
        ) {
            threadRepository.save(Thread.create(user))
        } else {
            latest
        }
    }

    private fun buildMessages(history: List<Chat>, currentQuestion: String): List<OpenAiMessage> {
        val messages = mutableListOf<OpenAiMessage>()
        messages.add(OpenAiMessage(role = "system", content = SYSTEM_PROMPT))
        history.forEach { chat ->
            messages.add(OpenAiMessage(role = "user", content = chat.question))
            messages.add(OpenAiMessage(role = "assistant", content = chat.answer))
        }
        messages.add(OpenAiMessage(role = "user", content = currentQuestion))
        return messages
    }

    @Transactional
    fun saveStreamedChat(thread: Thread, userId: UUID, question: String, answer: String, model: String) {
        val user = userRepository.findById(userId).orElseThrow()
        val chat = chatRepository.save(
            Chat(thread = thread, user = user, question = question, answer = answer, model = model)
        )
        thread.lastChatAt = chat.createdAt
    }
}
