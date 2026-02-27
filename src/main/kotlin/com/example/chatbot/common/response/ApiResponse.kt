package com.example.chatbot.common.response

data class ApiResponse<T>(
    val data: T,
)

data class ErrorResponse(
    val code: String,
    val message: String,
)

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
