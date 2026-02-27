package com.example.chatbot.common.security

import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

object SecurityUtil {
    fun getCurrentUserId(): UUID {
        return SecurityContextHolder.getContext().authentication.principal as UUID
    }

    fun getCurrentUserRole(): String {
        return SecurityContextHolder.getContext().authentication.authorities
            .first().authority.removePrefix("ROLE_")
    }

    fun isAdmin(): Boolean = getCurrentUserRole() == "ADMIN"
}
