package com.example.chatbot.domain.analytics.controller

import com.example.chatbot.common.exception.ForbiddenException
import com.example.chatbot.common.security.SecurityUtil
import com.example.chatbot.domain.analytics.service.ActivityStatsResponse
import com.example.chatbot.domain.analytics.service.AnalyticsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Tag(name = "Analytics", description = "분석 & 보고 API (ADMIN 전용)")
@RestController
@RequestMapping("/api/v1/admin/analytics")
class AnalyticsController(
    private val analyticsService: AnalyticsService,
) {
    @Operation(summary = "사용자 활동 통계 (ADMIN)", description = "최근 24시간의 회원가입·로그인·대화 생성 수를 반환합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "통계 조회 성공"),
        ApiResponse(responseCode = "403", description = "ADMIN 권한 필요"),
    )
    @GetMapping("/activity")
    fun getActivity(): ActivityStatsResponse {
        if (!SecurityUtil.isAdmin()) throw ForbiddenException()
        return analyticsService.getActivityStats()
    }

    @Operation(summary = "CSV 보고서 다운로드 (ADMIN)", description = "최근 24시간의 대화 목록을 CSV로 다운로드합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "보고서 다운로드 성공"),
        ApiResponse(responseCode = "403", description = "ADMIN 권한 필요"),
    )
    @GetMapping("/report")
    fun getReport(): ResponseEntity<ByteArray> {
        if (!SecurityUtil.isAdmin()) throw ForbiddenException()

        val csv = analyticsService.generateCsvReport()
        val filename = "report_${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.csv"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv.toByteArray(Charsets.UTF_8))
    }
}
