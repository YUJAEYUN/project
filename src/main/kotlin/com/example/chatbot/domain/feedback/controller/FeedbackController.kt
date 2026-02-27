package com.example.chatbot.domain.feedback.controller

import com.example.chatbot.common.response.ErrorResponse
import com.example.chatbot.common.response.PageResponse
import com.example.chatbot.common.security.SecurityUtil
import com.example.chatbot.domain.auth.entity.Role
import com.example.chatbot.domain.feedback.dto.CreateFeedbackRequest
import com.example.chatbot.domain.feedback.dto.FeedbackResponse
import com.example.chatbot.domain.feedback.dto.FeedbackStatusResponse
import com.example.chatbot.domain.feedback.dto.UpdateFeedbackStatusRequest
import com.example.chatbot.domain.feedback.service.FeedbackService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Feedback", description = "피드백 API")
@RestController
@RequestMapping("/api/v1/feedbacks")
class FeedbackController(
    private val feedbackService: FeedbackService,
) {
    @Operation(summary = "피드백 생성", description = "특정 대화에 피드백을 생성합니다. MEMBER는 본인 대화에만 가능합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "피드백 생성 성공"),
        ApiResponse(responseCode = "403", description = "본인 대화 아님", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "대화 없음", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "중복 피드백", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createFeedback(@Valid @RequestBody request: CreateFeedbackRequest): FeedbackResponse {
        val userId = SecurityUtil.getCurrentUserId()
        val role = Role.valueOf(SecurityUtil.getCurrentUserRole())
        return feedbackService.createFeedback(userId, role, request)
    }

    @Operation(summary = "피드백 목록 조회", description = "MEMBER는 본인 피드백만, ADMIN은 전체 피드백을 조회합니다.")
    @ApiResponses(ApiResponse(responseCode = "200", description = "조회 성공"))
    @GetMapping
    fun getFeedbacks(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "desc") sort: String,
        @RequestParam(required = false) isPositive: Boolean?,
    ): PageResponse<FeedbackResponse> {
        val userId = SecurityUtil.getCurrentUserId()
        val role = Role.valueOf(SecurityUtil.getCurrentUserRole())
        return feedbackService.getFeedbacks(userId, role, isPositive, page, size, sort)
    }

    @Operation(summary = "피드백 상태 변경 (ADMIN 전용)", description = "피드백 상태를 PENDING 또는 RESOLVED로 변경합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "상태 변경 성공"),
        ApiResponse(responseCode = "403", description = "ADMIN 권한 필요"),
        ApiResponse(responseCode = "404", description = "피드백 없음"),
    )
    @PatchMapping("/{feedbackId}/status")
    fun updateStatus(
        @PathVariable feedbackId: UUID,
        @Valid @RequestBody request: UpdateFeedbackStatusRequest,
    ): FeedbackStatusResponse {
        if (!SecurityUtil.isAdmin()) throw com.example.chatbot.common.exception.ForbiddenException()
        return feedbackService.updateStatus(feedbackId, request)
    }
}
