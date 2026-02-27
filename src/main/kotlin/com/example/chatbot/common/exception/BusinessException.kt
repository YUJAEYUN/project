package com.example.chatbot.common.exception

open class BusinessException(
    val code: String,
    message: String,
    val httpStatus: Int = 400,
) : RuntimeException(message)

class EmailDuplicatedException : BusinessException("EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다.", 409)
class InvalidCredentialsException : BusinessException("INVALID_CREDENTIALS", "이메일 또는 패스워드가 올바르지 않습니다.", 401)
class ForbiddenException : BusinessException("FORBIDDEN", "접근 권한이 없습니다.", 403)
class ThreadNotFoundException : BusinessException("THREAD_NOT_FOUND", "존재하지 않는 스레드입니다.", 404)
class ChatNotFoundException : BusinessException("CHAT_NOT_FOUND", "존재하지 않는 대화입니다.", 404)
class FeedbackNotFoundException : BusinessException("FEEDBACK_NOT_FOUND", "존재하지 않는 피드백입니다.", 404)
class FeedbackDuplicatedException : BusinessException("FEEDBACK_DUPLICATED", "이미 해당 대화에 피드백을 작성하셨습니다.", 409)
class OpenAiException(message: String) : BusinessException("OPENAI_ERROR", message, 502)
