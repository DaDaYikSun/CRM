package com.dadayiksun.crm.common.error;

import java.util.List;

import com.dadayiksun.crm.common.response.ErrorResponse;

/**
 * 업무 코드에서 던지는 예외. 응답은 {@link GlobalExceptionHandler} 한 곳에서 만든다 (K16 ②).
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<ErrorResponse.FieldError> fields;

    /** 정해진 문구가 있는 코드만. VALIDATION_FAILED · INTERNAL_ERROR는 문구를 함께 넘긴다 (응답에서 message가 빠지지 않게). */
    public ApiException(ErrorCode code) {
        this(code, code.message(), List.of());
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<ErrorResponse.FieldError> fields) {
        super(message);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(code + "는 정해진 문구가 없어 message를 넘겨야 한다 (C1 { code, message })");
        }
        this.code = code;
        this.fields = fields;
    }

    /** 필수 칸 빠짐: "{항목}을(를) 입력해 주세요." (정책 4-2). */
    public static ApiException required(String field, String fieldLabel) {
        String message = ErrorCode.required(fieldLabel);
        return new ApiException(ErrorCode.VALIDATION_FAILED, message,
                List.of(new ErrorResponse.FieldError(field, message)));
    }

    public ErrorCode code() {
        return code;
    }

    public List<ErrorResponse.FieldError> fields() {
        return fields;
    }
}
