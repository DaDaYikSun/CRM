package com.dadayiksun.crm.common.error;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.dadayiksun.crm.common.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 오류 응답을 만드는 한 곳 (K16 ②, API 설계 1-2 · 1-5).
 *
 * <p>입력 검사(@Valid) 규칙: {@code @NotBlank · @NotNull · @NotEmpty}의 message에는 칸 이름만 적는다
 * (예: {@code @NotBlank(message = "아이 이름")}). 이 클래스가 "{항목}을(를) 입력해 주세요."로 만든다 (정책 4-2).
 * 그 밖의 검사는 message를 그대로 쓴다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Set<String> REQUIRED_CONSTRAINTS = Set.of("NotBlank", "NotNull", "NotEmpty");

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return respond(e.code(), e.getMessage(), e.fields());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fieldMessage(fe.getCode(), fe.getDefaultMessage())))
                .toList();
        String message = fields.isEmpty() ? ErrorCode.VALIDATION_FALLBACK_MESSAGE : fields.get(0).message();
        return respond(ErrorCode.VALIDATION_FAILED, message, fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadInput(Exception e) {
        return respond(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FALLBACK_MESSAGE, List.of());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
        return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.message(), List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleDenied(AccessDeniedException e) {
        return respond(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.message(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("처리하지 못한 오류: {} {}", request.getMethod(), request.getRequestURI(), e);
        boolean read = HttpMethod.GET.matches(request.getMethod());
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.internalErrorMessage(read), List.of());
    }

    static String fieldMessage(String constraint, String annotationMessage) {
        if (constraint != null && REQUIRED_CONSTRAINTS.contains(constraint) && annotationMessage != null) {
            return ErrorCode.required(annotationMessage);
        }
        return annotationMessage != null ? annotationMessage : ErrorCode.VALIDATION_FALLBACK_MESSAGE;
    }

    private static ResponseEntity<ErrorResponse> respond(ErrorCode code, String message,
            List<ErrorResponse.FieldError> fields) {
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code.name(), message, fields));
    }
}
