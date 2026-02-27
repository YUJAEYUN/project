package com.example.chatbot.domain.chat.controller

import com.example.chatbot.common.response.ErrorResponse
import com.example.chatbot.common.response.PageResponse
import com.example.chatbot.common.security.SecurityUtil
import com.example.chatbot.domain.auth.entity.Role
import com.example.chatbot.domain.chat.dto.ChatResponse
import com.example.chatbot.domain.chat.dto.CreateChatRequest
import com.example.chatbot.domain.chat.dto.ThreadWithChatsResponse
import com.example.chatbot.domain.chat.service.ChatService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.Executors

@Tag(name = "Chat", description = "대화 API")
@RestController
@RequestMapping("/api/v1")
class ChatController(
    private val chatService: ChatService,
) {
    @Operation(
        summary = "대화 생성",
        description = """
        질문을 입력받고 AI 답변을 생성합니다.
        - `isStreaming=false` (기본): JSON 응답
        - `isStreaming=true`: SSE(text/event-stream) 응답
        """
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "대화 생성 성공"),
        ApiResponse(
            responseCode = "400", description = "question 누락",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "502", description = "OpenAI API 오류",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
    )
    @PostMapping("/chats", produces = [MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE])
    fun createChat(
        @Valid @RequestBody request: CreateChatRequest,
    ): Any {
        val userId = SecurityUtil.getCurrentUserId()

        return if (request.isStreaming) {
            createStreamingResponse(userId, request)
        } else {
            chatService.createChat(userId, request).also {
                // HTTP 201 for non-streaming is handled by ResponseStatus
            }
        }
    }

    private fun createStreamingResponse(userId: UUID, request: CreateChatRequest): SseEmitter {
        val emitter = SseEmitter(60_000L)
        val executor = Executors.newSingleThreadExecutor()

        executor.execute {
            try {
                chatService.createChatStream(userId, request)
                    .subscribe(
                        { chunk -> emitter.send(SseEmitter.event().data(chunk)) },
                        { error -> emitter.completeWithError(error) },
                        { emitter.complete() }
                    )
            } catch (e: Exception) {
                emitter.completeWithError(e)
            }
        }
        return emitter
    }

    @Operation(summary = "대화 목록 조회", description = "스레드 단위로 그룹화된 대화 목록을 조회합니다. ADMIN은 모든 유저의 대화를 조회할 수 있습니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 필요"),
    )
    @GetMapping("/chats")
    fun getChats(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "desc") sort: String,
        @RequestParam(required = false) userId: UUID?,
    ): PageResponse<ThreadWithChatsResponse> {
        val currentUserId = SecurityUtil.getCurrentUserId()
        val currentRole = Role.valueOf(SecurityUtil.getCurrentUserRole())
        return chatService.getThreads(currentUserId, currentRole, userId, page, size, sort)
    }

    @Operation(summary = "스레드 삭제", description = "특정 스레드와 하위 대화를 삭제합니다. MEMBER는 본인 스레드만 삭제 가능합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "삭제 성공"),
        ApiResponse(responseCode = "403", description = "권한 없음"),
        ApiResponse(responseCode = "404", description = "스레드 없음"),
    )
    @DeleteMapping("/threads/{threadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteThread(@PathVariable threadId: UUID) {
        val userId = SecurityUtil.getCurrentUserId()
        val role = Role.valueOf(SecurityUtil.getCurrentUserRole())
        chatService.deleteThread(userId, role, threadId)
    }
}
