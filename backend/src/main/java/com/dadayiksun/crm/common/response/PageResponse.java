package com.dadayiksun.crm.common.response;

import java.util.List;

import com.dadayiksun.crm.common.web.PageQuery;

/**
 * 목록 {@code { "items": [...], "page": 1, "size": 20, "total": 57 }} (API 설계 1-2 · 1-3).
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <T> PageResponse<T> of(List<T> items, PageQuery query, long total) {
        return new PageResponse<>(items, query.page(), PageQuery.SIZE, total);
    }
}
