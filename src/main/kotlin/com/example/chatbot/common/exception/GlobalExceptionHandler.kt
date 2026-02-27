package com.example.chatbot.common.exception

import com.example.chatbot.common.response.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(e.httpStatus)
            .body(ErrorResponse(code = e.code, message = e.message ?: "오류가 발생했습니다."))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.allErrors
            .filterIsInstance<FieldError>()
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(code = "INVALID_REQUEST", message = message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(code = "INVALID_REQUEST", message = "요청 본문을 읽을 수 없습니다."))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(code = "INTERNAL_ERROR", message = "서버 오류가 발생했습니다."))
    }
}
