package com.example.chatbot.domain.auth.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.OffsetDateTime
import java.util.UUID

enum class Role { MEMBER, ADMIN }

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    var password: String,

    @Column(nullable = false)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role = Role.MEMBER,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
