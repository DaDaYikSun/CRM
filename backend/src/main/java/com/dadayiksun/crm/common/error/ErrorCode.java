package com.dadayiksun.crm.common.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드와 문구 (API 설계 1-5). 문구는 정책 정의서 4-2 그대로다. 바꾸지 않는다 (CLAUDE.md 3번).
 * VALIDATION_FAILED의 문구는 칸마다 다르다 (필수 칸: "{항목}을(를) 입력해 주세요.").
 * INTERNAL_ERROR는 요청 종류(조회 · 저장)로 문구를 고른다 ({@link #internalErrorMessage(boolean)}).
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, null),
    PASSWORD_RULE(HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상, 영문과 숫자를 섞어 주세요."),
    RULE_VALUE_INVALID(HttpStatus.BAD_REQUEST, "값이 비었거나 0 이하라 저장하지 못했습니다. 1 이상의 숫자를 입력해 주세요."),
    TEMPLATE_PLACEHOLDER_MISSING(HttpStatus.BAD_REQUEST, "필수 칸이 빠져 저장하지 못했습니다. 문구에 '{아이 이름}'과 '{예정일}'을 넣어 주세요."),
    REPORT_SECTION_REQUIRED(HttpStatus.BAD_REQUEST, "고른 항목이 없어 저장하지 못했습니다. 리포트에 담을 항목을 하나 이상 골라 주세요."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 맞지 않습니다."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "이 작업을 할 권한이 없습니다. 필요하면 관리자에게 문의해 주세요."),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "임시 비밀번호로 로그인했습니다. 새 비밀번호를 정해 주세요."),
    EDIT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "본인이 남긴 기록만, 남긴 날에만 고칠 수 있습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾는 정보가 없습니다. 목록에서 다시 찾아 주세요."),
    SMS_NOT_AGREED(HttpStatus.CONFLICT, "안내 문자 수신에 동의하지 않은 보호자입니다. 전화로 연락해 주세요."),
    PET_RAINBOW(HttpStatus.CONFLICT, "무지개다리로 표시된 아이라 기록을 남길 수 없습니다. 기록이 필요하면 관리자에게 표시 되돌리기를 요청해 주세요."),
    LOGIN_ID_TAKEN(HttpStatus.CONFLICT, "이미 쓰는 아이디입니다. 다른 아이디를 입력해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, null);

    /**
     * [?] 형식 틀림 · 정해진 값 밖 · page 오류처럼 칸 이름으로 문구를 만들 수 없는 VALIDATION_FAILED의 임시 문구.
     * 정책 4-2에 공통 문구가 없다 (API 설계 7장 18번, 정책 담당). 정해지면 이 값만 바꾼다.
     */
    public static final String VALIDATION_FALLBACK_MESSAGE = "입력한 값을 확인해 주세요.";

    /** 정책 4-2 '필수 항목'. {항목}에 칸 이름을 넣는다. */
    public static final String REQUIRED_TEMPLATE = "%s을(를) 입력해 주세요.";

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    public static String required(String fieldLabel) {
        return REQUIRED_TEMPLATE.formatted(fieldLabel);
    }

    /** 정책 4-2 '저장 실패' · '조회 실패' (API 설계 1-5 INTERNAL_ERROR). */
    public static String internalErrorMessage(boolean readRequest) {
        return readRequest ? "불러오지 못했습니다. 다시 시도해 주세요." : "저장하지 못했습니다. 다시 시도해 주세요.";
    }
}
