package com.dadayiksun.crm.search.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.search.dto.SearchItem;
import com.dadayiksun.crm.search.service.SearchService;

/** 통합 검색 (FR-SRCH-001 · 002). 미용사도 쓴다 (T3, POL-ACC-011) — 역할 제한을 두지 않는다. */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @GetMapping("/preview")
    public ApiResponse<List<SearchItem>> preview(@RequestParam(required = false) String q) {
        return ApiResponse.of(service.preview(q));
    }

    @GetMapping
    public ApiResponse<PageResponse<SearchItem>> search(
            @RequestParam(required = false) String q, @RequestParam(required = false) String page) {
        return ApiResponse.of(service.search(q, PageQuery.parse(page)));
    }
}
