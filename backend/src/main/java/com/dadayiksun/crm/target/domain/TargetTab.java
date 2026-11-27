package com.dadayiksun.crm.target.domain;

import java.util.Locale;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;

/** 연락 대상 목록 탭 (S-03, POL-TGT-001). 주소 {@code ?tab=today|week|missed}. 없으면 오늘 탭 (SCR-S03-03). */
public enum TargetTab {
    TODAY, WEEK, MISSED;

    public static TargetTab parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return TODAY;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "today" -> TODAY;
            case "week" -> WEEK;
            case "missed" -> MISSED;
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FALLBACK_MESSAGE);
        };
    }
}
