// 로그인 · 현재 직원 API (계약 C3, API 설계 3-1). 세션 쿠키만 쓰고 토큰은 브라우저 저장소에 두지 않는다 (K2).
import type { Role } from "@/types/common";
import { api } from "./client";

export interface CurrentStaff {
  staffId: number;
  name: string;
  role: Role;
  mustChangePassword: boolean;
}

export function login(loginId: string, password: string): Promise<CurrentStaff> {
  return api.post<CurrentStaff>("/api/auth/login", { loginId, password });
}

export function fetchMe(): Promise<CurrentStaff> {
  return api.get<CurrentStaff>("/api/auth/me");
}

/** POST /api/auth/password — 새 비밀번호 정하기 (POL-ACC-010, #150). 조건에 안 맞으면 400 PASSWORD_RULE */
export function changePassword(newPassword: string, idempotencyKey?: string): Promise<null> {
  return api.post<null>("/api/auth/password", { newPassword }, { idempotencyKey });
}

/** 로그아웃 (POL-ACC-008, #167). 로그인이 없거나 만료됐어도 성공한다 */
export function logout(): Promise<void> {
  return api.post<void>("/api/auth/logout");
}
