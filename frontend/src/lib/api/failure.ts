// 실패를 화면 문구와 [다시 시도] 여부로 바꾼다 (FR-COM-001, POL-ERR-001 · 002, 정책 4-2, #168).
import { ApiError, LOAD_FAILED_MESSAGE, SAVE_FAILED_MESSAGE } from "./client";
import type { FieldError } from "@/types/common";

export interface Failure {
  message: string;
  /** [다시 시도]를 보일지. 네트워크 · 서버 오류만 다시 해 볼 만하다 */
  retry: boolean;
  /** 입력 칸 오류 (400 VALIDATION_FAILED 등). 칸 아래에 보인다 */
  fields: FieldError[];
}

/** 네트워크 오류(status 0) · 서버 오류(5xx) */
export function isTransient(error: unknown): boolean {
  return !(error instanceof ApiError) || error.status === 0 || error.status >= 500;
}

/**
 * 조회 실패. client가 이미 한 번 다시 부른 뒤다 (POL-ERR-001).
 * 네트워크 · 서버 오류는 조회 실패 문구 + [다시 시도], 권한 없음 · 없는 정보 같은 4xx는 서버 문구만.
 */
export function loadFailure(error: unknown): Failure {
  if (isTransient(error)) return { message: LOAD_FAILED_MESSAGE, retry: true, fields: [] };
  const e = error as ApiError;
  return { message: e.message, retry: false, fields: e.fields };
}

/**
 * 저장 실패. 자동으로 다시 부르지 않는다 (POL-ERR-002).
 * 네트워크 · 서버 오류는 저장 실패 문구 + [다시 시도], 입력 오류 · 중복 같은 4xx는 서버 문구(4-2)와 칸 오류만.
 */
export function saveFailure(error: unknown): Failure {
  if (isTransient(error)) return { message: SAVE_FAILED_MESSAGE, retry: true, fields: [] };
  const e = error as ApiError;
  return { message: e.message, retry: false, fields: e.fields };
}
