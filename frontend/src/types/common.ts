// 공통 응답 타입 (C1, API 설계 1-2 · 1-5)

/** 성공: { data } */
export interface ApiSuccess<T> {
  data: T;
}

/** 목록: data 안의 { items, page, size(20), total } */
export interface PageData<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

/** 오류 코드 (API 설계 1-5) */
export type ErrorCode =
  | "VALIDATION_FAILED"
  | "PASSWORD_RULE"
  | "RULE_VALUE_INVALID"
  | "TEMPLATE_PLACEHOLDER_MISSING"
  | "REPORT_SECTION_REQUIRED"
  | "LOGIN_FAILED"
  | "SESSION_EXPIRED"
  | "FORBIDDEN"
  | "PASSWORD_CHANGE_REQUIRED"
  | "EDIT_NOT_ALLOWED"
  | "NOT_FOUND"
  | "SMS_NOT_AGREED"
  | "PET_RAINBOW"
  | "LOGIN_ID_TAKEN"
  | "OPTION_DUPLICATE"
  | "INTERNAL_ERROR";

export interface FieldError {
  field: string;
  message: string;
}

/** 실패: { error: { code, message, fields? } } */
export interface ApiErrorBody {
  error: {
    code: ErrorCode;
    message: string;
    fields?: FieldError[];
  };
}

/** 역할 (POL-ACC-001) */
export type Role = "ADMIN" | "DESK" | "GROOMER";
