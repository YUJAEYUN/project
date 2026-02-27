package com.example.chatbot.domain.chat.entity

import com.example.chatbot.domain.auth.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "threads")
class Thread(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(nullable = false)
    var lastChatAt: OffsetDateTime = OffsetDateTime.now(),

    @OneToMany(mappedBy = "thread", cascade = [CascadeType.ALL], orphanRemoval = true)
    val chats: MutableList<Chat> = mutableListOf(),
) {
    companion object {
        fun create(user: User): Thread = Thread(user = user)
    }
}
