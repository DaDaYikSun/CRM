package com.dadayiksun.crm.common.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 실패 응답 {@code { "error": { "code", "message", "fields"? } }} (API 설계 1-2).
 * fields는 입력 검사 실패 때만 있다.
 */
public record ErrorResponse(Body error) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Body(String code, String message, List<FieldError> fields) {
    }

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new Body(code, message, List.of()));
    }

    public static ErrorResponse of(String code, String message, List<FieldError> fields) {
        return new ErrorResponse(new Body(code, message, fields));
    }
}
