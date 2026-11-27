package com.dadayiksun.crm.dashboard.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;

/**
 * 지표 기간 (양 끝 포함, 한국 날짜). 둘 다 없으면 이번 달 (화면 설계 SCR-S02-03 '처음: 기간 이번 달', API 설계 3-8).
 * 하나만 오면 빠진 쪽을 같은 달의 처음 · 끝으로 채운다. 형식이 틀리거나 from > to면 400 VALIDATION_FAILED.
 * 오늘(한국 날짜)보다 뒤는 세지 않는다: to가 오늘 뒤면 오늘로 자르고, from이 오늘 뒤면 400 (#176 결정, 이번 달 기본값도 오늘까지).
 */
public record MetricsPeriod(LocalDate from, LocalDate to) {

    public static MetricsPeriod parse(String fromRaw, String toRaw, LocalDate today) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        LocalDate from = date(fromRaw, "from", errors);
        LocalDate to = date(toRaw, "to", errors);
        if (!errors.isEmpty()) {
            throw invalid(errors);
        }
        if (from == null && to == null) {
            from = today.withDayOfMonth(1);
            to = today.withDayOfMonth(today.lengthOfMonth());
        } else if (from == null) {
            from = to.withDayOfMonth(1);
        } else if (to == null) {
            to = from.withDayOfMonth(from.lengthOfMonth());
        }
        if (from.isAfter(today)) {
            throw invalid(List.of(new ErrorResponse.FieldError("from", ErrorCode.VALIDATION_FALLBACK_MESSAGE)));
        }
        if (to.isAfter(today)) {
            to = today;
        }
        if (from.isAfter(to)) {
            throw invalid(List.of(new ErrorResponse.FieldError("from", ErrorCode.VALIDATION_FALLBACK_MESSAGE)));
        }
        return new MetricsPeriod(from, to);
    }

    private static LocalDate date(String raw, String field, List<ErrorResponse.FieldError> errors) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            errors.add(new ErrorResponse.FieldError(field, ErrorCode.VALIDATION_FALLBACK_MESSAGE));
            return null;
        }
    }

    private static ApiException invalid(List<ErrorResponse.FieldError> fields) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FALLBACK_MESSAGE, fields);
    }
}
