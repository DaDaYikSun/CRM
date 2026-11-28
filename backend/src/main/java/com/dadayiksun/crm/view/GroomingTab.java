package com.dadayiksun.crm.view;

import java.util.Locale;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;

/** 미용 목록 탭 (S-11, SCR-S11-02). 주소 {@code ?tab=today|week}. 없으면 오늘 탭 (SCR-S11-03). */
public enum GroomingTab {
    TODAY, WEEK;

    public static GroomingTab parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return TODAY;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "today" -> TODAY;
            case "week" -> WEEK;
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FALLBACK_MESSAGE);
        };
    }
}
