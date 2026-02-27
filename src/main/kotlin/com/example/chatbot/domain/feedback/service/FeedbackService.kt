package com.example.chatbot.domain.feedback.service

import com.example.chatbot.common.exception.ChatNotFoundException
import com.example.chatbot.common.exception.FeedbackDuplicatedException
import com.example.chatbot.common.exception.FeedbackNotFoundException
import com.example.chatbot.common.exception.ForbiddenException
import com.example.chatbot.common.response.PageResponse
import com.example.chatbot.domain.auth.entity.Role
import com.example.chatbot.domain.auth.repository.UserRepository
import com.example.chatbot.domain.chat.repository.ChatRepository
import com.example.chatbot.domain.feedback.dto.CreateFeedbackRequest
import com.example.chatbot.domain.feedback.dto.FeedbackResponse
import com.example.chatbot.domain.feedback.dto.FeedbackStatusResponse
import com.example.chatbot.domain.feedback.dto.UpdateFeedbackStatusRequest
import com.example.chatbot.domain.feedback.entity.Feedback
import com.example.chatbot.domain.feedback.repository.FeedbackRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createFeedback(userId: UUID, role: Role, request: CreateFeedbackRequest): FeedbackResponse {
        val user = userRepository.findById(userId).orElseThrow()
        val chat = chatRepository.findById(request.chatId).orElseThrow { ChatNotFoundException() }

        if (role != Role.ADMIN && chat.user.id != userId) {
            throw ForbiddenException()
        }
        if (feedbackRepository.existsByUserIdAndChatId(userId, request.chatId)) {
            throw FeedbackDuplicatedException()
        }

        val feedback = feedbackRepository.save(
            Feedback(user = user, chat = chat, isPositive = request.isPositive)
        )
        return FeedbackResponse.from(feedback)
    }

    fun getFeedbacks(
        userId: UUID,
        role: Role,
        isPositive: Boolean?,
        page: Int,
        size: Int,
        sort: String,
    ): PageResponse<FeedbackResponse> {
        val direction = if (sort == "asc") Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(page, size, Sort.by(direction, "createdAt"))

        val feedbackPage = if (role == Role.ADMIN) {
            feedbackRepository.findAllByOptionalIsPositive(isPositive, pageable)
        } else {
            feedbackRepository.findAllByUserIdAndOptionalIsPositive(userId, isPositive, pageable)
        }

        return PageResponse(
            content = feedbackPage.content.map { FeedbackResponse.from(it) },
            page = feedbackPage.number,
            size = feedbackPage.size,
            totalElements = feedbackPage.totalElements,
            totalPages = feedbackPage.totalPages,
        )
    }

    @Transactional
    fun updateStatus(feedbackId: UUID, request: UpdateFeedbackStatusRequest): FeedbackStatusResponse {
        val feedback = feedbackRepository.findById(feedbackId)
            .orElseThrow { FeedbackNotFoundException() }
        feedback.status = request.status
        return FeedbackStatusResponse(id = feedback.id, status = feedback.status)
    }
}
