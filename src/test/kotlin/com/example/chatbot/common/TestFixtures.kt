package com.example.chatbot.common

import com.example.chatbot.domain.auth.entity.Role
import com.example.chatbot.domain.auth.entity.User
import com.example.chatbot.domain.chat.entity.Chat
import com.example.chatbot.domain.chat.entity.Thread
import java.time.OffsetDateTime
import java.util.UUID

object TestFixtures {
    fun createUser(
        email: String = "user@example.com",
        password: String = "\$2a\$10\$encoded",
        name: String = "홍길동",
        role: Role = Role.MEMBER,
    ) = User(
        id = UUID.randomUUID(),
        email = email,
        password = password,
        name = name,
        role = role,
    )

    fun createAdminUser(
        email: String = "admin@example.com",
        name: String = "관리자",
    ) = createUser(email = email, name = name, role = Role.ADMIN)

    fun createThread(
        user: User,
        lastChatAt: OffsetDateTime = OffsetDateTime.now(),
    ) = Thread(
        id = UUID.randomUUID(),
        user = user,
        lastChatAt = lastChatAt,
    )

    fun createChat(
        thread: Thread,
        user: User,
        question: String = "안녕하세요?",
        answer: String = "안녕하세요! 무엇을 도와드릴까요?",
        model: String = "gpt-4o",
    ) = Chat(
        id = UUID.randomUUID(),
        thread = thread,
        user = user,
        question = question,
        answer = answer,
        model = model,
    )
}
