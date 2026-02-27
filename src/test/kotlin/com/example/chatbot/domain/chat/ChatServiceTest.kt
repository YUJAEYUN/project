package com.example.chatbot.domain.chat

import com.example.chatbot.common.TestFixtures
import com.example.chatbot.common.exception.ForbiddenException
import com.example.chatbot.common.exception.OpenAiException
import com.example.chatbot.common.exception.ThreadNotFoundException
import com.example.chatbot.domain.analytics.repository.ActivityLogRepository
import com.example.chatbot.domain.auth.entity.Role
import com.example.chatbot.domain.auth.repository.UserRepository
import com.example.chatbot.domain.chat.dto.CreateChatRequest
import com.example.chatbot.domain.chat.repository.ChatRepository
import com.example.chatbot.domain.chat.repository.ThreadRepository
import com.example.chatbot.domain.chat.service.ChatService
import com.example.chatbot.infrastructure.openai.OpenAiClient
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.util.Optional

class ChatServiceTest {

    private val threadRepository: ThreadRepository = mockk()
    private val chatRepository: ChatRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val openAiClient: OpenAiClient = mockk()
    private val activityLogRepository: ActivityLogRepository = mockk()
    private val vectorStore: VectorStore = mockk()
    private val defaultModel = "gpt-4o-mini"

    private val chatService = ChatService(
        threadRepository, chatRepository, userRepository, openAiClient, activityLogRepository, vectorStore, defaultModel
    )

    private val user = TestFixtures.createUser()

    @BeforeEach
    fun setUp() {
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { vectorStore.similaritySearch(any<SearchRequest>()) } returns emptyList()
    }

    // ─── 스레드 결정 로직 ────────────────────────────────────────

    @Test
    fun `첫 질문 - 스레드가 없으면 새 스레드 생성`() {
        val newThread = TestFixtures.createThread(user)
        every { threadRepository.findTopByUserIdOrderByLastChatAtDesc(user.id) } returns null
        every { threadRepository.save(any<com.example.chatbot.domain.chat.entity.Thread>()) } returns newThread
        every { chatRepository.findAllByThreadIdOrderByCreatedAtAsc(newThread.id) } returns emptyList()
        every { openAiClient.complete(any(), any()) } returns "답변입니다."
        every { chatRepository.save(any()) } answers { firstArg() }
        every { activityLogRepository.save(any()) } answers { firstArg() }

        chatService.createChat(user.id, CreateChatRequest("첫 질문입니다."))

        verify { threadRepository.save(any<com.example.chatbot.domain.chat.entity.Thread>()) }
    }

    @Test
    fun `30분 이내 - 기존 스레드 재사용`() {
        val existingThread = TestFixtures.createThread(user, lastChatAt = OffsetDateTime.now().minusMinutes(10))
        every { threadRepository.findTopByUserIdOrderByLastChatAtDesc(user.id) } returns existingThread
        every { chatRepository.findAllByThreadIdOrderByCreatedAtAsc(existingThread.id) } returns emptyList()
        every { openAiClient.complete(any(), any()) } returns "답변입니다."
        every { chatRepository.save(any()) } answers { firstArg() }
        every { activityLogRepository.save(any()) } answers { firstArg() }

        chatService.createChat(user.id, CreateChatRequest("질문"))

        verify(exactly = 0) { threadRepository.save(any<com.example.chatbot.domain.chat.entity.Thread>()) }
    }

    @Test
    fun `30분 초과 - 새 스레드 생성`() {
        val oldThread = TestFixtures.createThread(user, lastChatAt = OffsetDateTime.now().minusMinutes(31))
        val newThread = TestFixtures.createThread(user)
        every { threadRepository.findTopByUserIdOrderByLastChatAtDesc(user.id) } returns oldThread
        every { threadRepository.save(any<com.example.chatbot.domain.chat.entity.Thread>()) } returns newThread
        every { chatRepository.findAllByThreadIdOrderByCreatedAtAsc(newThread.id) } returns emptyList()
        every { openAiClient.complete(any(), any()) } returns "답변입니다."
        every { chatRepository.save(any()) } answers { firstArg() }
        every { activityLogRepository.save(any()) } answers { firstArg() }

        chatService.createChat(user.id, CreateChatRequest("새 질문"))

        verify { threadRepository.save(any<com.example.chatbot.domain.chat.entity.Thread>()) }
    }

    @Test
    fun `정확히 30분 경계값 - 새 스레드 생성`() {
        val boundaryThread = TestFixtures.createThread(user, lastChatAt = OffsetDateTime.now().minusMinutes(30))
        val newThread = TestFixtures.createThread(user)
        every { threadRepository.findTopByUserIdOrderByLastChatAtDesc(user.id) } returns boundaryThread
        every { threadRepository.save(any<com.example.chatbot.domain.chat.entity.Thread>()) } returns newThread
        every { chatRepository.findAllByThreadIdOrderByCreatedAtAsc(newThread.id) } returns emptyList()
        every { openAiClient.complete(any(), any()) } returns "답변입니다."
        every { chatRepository.save(any()) } answers { firstArg() }
        every { activityLogRepository.save(any()) } answers { firstArg() }

        chatService.createChat(user.id, CreateChatRequest("경계 질문"))

        verify { threadRepository.save(any<com.example.chatbot.domain.chat.entity.Thread>()) }
    }

    // ─── 대화 생성 ────────────────────────────────────────────

    @Test
    fun `정상 대화 생성 성공`() {
        val thread = TestFixtures.createThread(user)
        every { threadRepository.findTopByUserIdOrderByLastChatAtDesc(user.id) } returns thread
        every { chatRepository.findAllByThreadIdOrderByCreatedAtAsc(thread.id) } returns emptyList()
        every { openAiClient.complete(any(), any()) } returns "AI 답변입니다."
        every { chatRepository.save(any()) } answers { firstArg() }
        every { activityLogRepository.save(any()) } answers { firstArg() }

        val result = chatService.createChat(user.id, CreateChatRequest("질문입니다."))

        assertThat(result.question).isEqualTo("질문입니다.")
        assertThat(result.answer).isEqualTo("AI 답변입니다.")
        assertThat(result.model).isEqualTo(defaultModel)
    }

    @Test
    fun `커스텀 model 파라미터 적용`() {
        val thread = TestFixtures.createThread(user)
        every { threadRepository.findTopByUserIdOrderByLastChatAtDesc(user.id) } returns thread
        every { chatRepository.findAllByThreadIdOrderByCreatedAtAsc(thread.id) } returns emptyList()
        every { openAiClient.complete("gpt-4-turbo", any()) } returns "GPT-4 Turbo 답변"
        every { chatRepository.save(any()) } answers { firstArg() }
        every { activityLogRepository.save(any()) } answers { firstArg() }

        val result = chatService.createChat(user.id, CreateChatRequest("질문", model = "gpt-4-turbo"))

        assertThat(result.model).isEqualTo("gpt-4-turbo")
    }

    @Test
    fun `OpenAI 오류 시 OpenAiException 발생`() {
        val thread = TestFixtures.createThread(user)
        every { threadRepository.findTopByUserIdOrderByLastChatAtDesc(user.id) } returns thread
        every { chatRepository.findAllByThreadIdOrderByCreatedAtAsc(thread.id) } returns emptyList()
        every { openAiClient.complete(any(), any()) } throws OpenAiException("API 오류")

        assertThrows<OpenAiException> {
            chatService.createChat(user.id, CreateChatRequest("질문"))
        }
    }

    @Test
    fun `이전 대화 히스토리가 messages에 포함된다`() {
        val thread = TestFixtures.createThread(user)
        val previousChat = TestFixtures.createChat(thread, user, "이전 질문", "이전 답변")
        val capturedMessages = mutableListOf<List<com.example.chatbot.infrastructure.openai.dto.OpenAiMessage>>()

        every { threadRepository.findTopByUserIdOrderByLastChatAtDesc(user.id) } returns thread
        every { chatRepository.findAllByThreadIdOrderByCreatedAtAsc(thread.id) } returns listOf(previousChat)
        every { openAiClient.complete(any(), capture(capturedMessages)) } returns "답변"
        every { chatRepository.save(any()) } answers { firstArg() }
        every { activityLogRepository.save(any()) } answers { firstArg() }

        chatService.createChat(user.id, CreateChatRequest("현재 질문"))

        val messages = capturedMessages.first()
        // system + user(이전질문) + assistant(이전답변) + user(현재질문) = 4
        assertThat(messages).hasSize(4)
        assertThat(messages[0].role).isEqualTo("system")
        assertThat(messages[1].content).isEqualTo("이전 질문")
        assertThat(messages[2].content).isEqualTo("이전 답변")
        assertThat(messages[3].content).isEqualTo("현재 질문")
    }

    // ─── 스레드 삭제 ────────────────────────────────────────────

    @Test
    fun `본인 스레드 삭제 성공`() {
        val thread = TestFixtures.createThread(user)
        every { threadRepository.findById(thread.id) } returns Optional.of(thread)
        every { threadRepository.delete(thread) } just Runs

        chatService.deleteThread(user.id, Role.MEMBER, thread.id)

        verify { threadRepository.delete(thread) }
    }

    @Test
    fun `타인 스레드 삭제 시도 - MEMBER - ForbiddenException`() {
        val anotherUser = TestFixtures.createUser(email = "other@example.com")
        val thread = TestFixtures.createThread(anotherUser)
        every { threadRepository.findById(thread.id) } returns Optional.of(thread)

        assertThrows<ForbiddenException> {
            chatService.deleteThread(user.id, Role.MEMBER, thread.id)
        }
    }

    @Test
    fun `ADMIN은 타인 스레드 삭제 가능`() {
        val anotherUser = TestFixtures.createUser(email = "other@example.com")
        val thread = TestFixtures.createThread(anotherUser)
        every { threadRepository.findById(thread.id) } returns Optional.of(thread)
        every { threadRepository.delete(thread) } just Runs

        chatService.deleteThread(user.id, Role.ADMIN, thread.id)

        verify { threadRepository.delete(thread) }
    }

    @Test
    fun `존재하지 않는 스레드 삭제 - ThreadNotFoundException`() {
        every { threadRepository.findById(any()) } returns Optional.empty()

        assertThrows<ThreadNotFoundException> {
            chatService.deleteThread(user.id, Role.MEMBER, java.util.UUID.randomUUID())
        }
    }
}
