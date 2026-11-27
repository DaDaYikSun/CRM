package com.dadayiksun.crm.dashboard.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.dashboard.dto.MetricsResponse;
import com.dadayiksun.crm.dashboard.service.DashboardMetricsService;

/**
 * 대시보드 지표 (S-02, C15, API 설계 3-8). 관리자 · 데스크만: 역할 검사(#143)의 기본 규칙이 미용사를 403으로 막는다
 * (NFR-SEC-002, POL-ACC-011, T10 · T11).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardMetricsService metrics;

    public DashboardController(DashboardMetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/metrics")
    public ApiResponse<MetricsResponse> metrics(@RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to) {
        return ApiResponse.of(metrics.metrics(from, to));
    }
}
