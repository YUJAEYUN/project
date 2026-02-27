package com.example.chatbot.domain.document.controller

import com.example.chatbot.common.exception.ForbiddenException
import com.example.chatbot.common.response.ApiResponse
import com.example.chatbot.common.security.SecurityUtil
import com.example.chatbot.domain.document.dto.CreateDocumentRequest
import com.example.chatbot.domain.document.dto.DocumentResponse
import com.example.chatbot.domain.document.service.DocumentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@Tag(name = "Documents", description = "RAG 지식 베이스 문서 관리 (ADMIN 전용)")
@RestController
@RequestMapping("/api/v1/documents")
@SecurityRequirement(name = "BearerAuth")
class DocumentController(
    private val documentService: DocumentService,
) {
    @Operation(
        summary = "문서 업로드 (ADMIN)",
        description = "RAG 검색에 활용할 문서를 업로드합니다. 자동으로 청킹 후 벡터 DB에 저장됩니다.",
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "문서 업로드 성공"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ADMIN 권한 필요"),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addDocument(@Valid @RequestBody request: CreateDocumentRequest): ApiResponse<DocumentResponse> {
        if (!SecurityUtil.isAdmin()) throw ForbiddenException()
        return ApiResponse(documentService.addDocument(request))
    }

    @Operation(
        summary = "문서 목록 조회 (ADMIN)",
        description = "업로드된 지식 베이스 문서 목록을 반환합니다.",
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ADMIN 권한 필요"),
    )
    @GetMapping
    fun getDocuments(): ApiResponse<List<DocumentResponse>> {
        if (!SecurityUtil.isAdmin()) throw ForbiddenException()
        return ApiResponse(documentService.getDocuments())
    }
}
