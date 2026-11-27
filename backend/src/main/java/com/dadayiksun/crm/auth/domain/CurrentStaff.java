package com.dadayiksun.crm.auth.domain;

import java.util.Set;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;

/**
 * 지금 로그인한 직원 (세션에서 꺼낸 값). Controller 인자로 받고, Service에는 {@code staffId}만 넘긴다.
 * 값에 따라 갈리는 역할 검사(접종 방문 · 접종 타임라인 등)는 각 API가 {@link #require}로 한다 (NFR-SEC-002).
 */
public record CurrentStaff(Long staffId, Role role) {

    /** 허용 역할이 아니면 403 FORBIDDEN (정책 4-2 '권한 없음' 문구). */
    public void require(Role... allowed) {
        if (!Set.of(allowed).contains(role)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }
}
