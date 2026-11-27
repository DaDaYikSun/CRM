package com.dadayiksun.crm.target.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.target.domain.TargetTab;
import com.dadayiksun.crm.target.dto.TargetLineResponse;
import com.dadayiksun.crm.target.dto.TargetSummaryResponse;
import com.dadayiksun.crm.target.service.TargetQueryService;

/**
 * 연락 대상 목록 (S-03, C11, API 설계 3-6). 관리자 · 데스크만 쓴다.
 * 미용사 403(NFR-SEC-002, POL-ACC-011)은 역할 검사(#143)가 들어오면 이 주소에 건다.
 */
@RestController
@RequestMapping("/api/targets")
public class TargetController {

    private final TargetQueryService service;

    public TargetController(TargetQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<TargetLineResponse>> list(@RequestParam(required = false) String tab,
                                                             @RequestParam(required = false) String page) {
        return ApiResponse.of(service.list(TargetTab.parse(tab), PageQuery.parse(page)));
    }

    @GetMapping("/summary")
    public ApiResponse<TargetSummaryResponse> summary() {
        return ApiResponse.of(service.summary());
    }
}
