// 백오피스 API (ADM, admin) — 관리자만 (API 설계 3-10). 공용 호출 함수 client.ts만 쓴다 (frontend/README.md).
// #150: 직원 관리 C16. 다른 이슈(초안 25 · 30 · 31 · 32 · 33)가 이 파일에 함수를 더한다.
import type { Role } from "@/types/common";
import { api, type SaveOptions } from "./client";

/** GET /api/admin/staff 한 줄 */
export interface StaffItem {
  staffId: number;
  loginId: string;
  name: string;
  role: Role;
  isActive: boolean;
  lastLoginAt: string | null;
  deactivatedAt: string | null;
}

export interface CreateStaffInput {
  loginId: string;
  name: string;
  role: Role;
}

/** POST /api/admin/staff 출력. 임시 비밀번호는 이때 한 번만 온다 (정책 4-4 B-01) */
export interface CreatedStaff {
  staffId: number;
  tempPassword: string;
}

export function listStaff(): Promise<StaffItem[]> {
  return api.get<StaffItem[]>("/api/admin/staff");
}

export function createStaff(input: CreateStaffInput, options?: SaveOptions): Promise<CreatedStaff> {
  return api.post<CreatedStaff>("/api/admin/staff", input, options);
}

/** PATCH /api/admin/staff/{id} — 바꿀 칸만 */
export function updateStaff(
  staffId: number,
  patch: { role?: Role; isActive?: boolean },
  options?: SaveOptions,
): Promise<null> {
  return api.patch<null>(`/api/admin/staff/${staffId}`, patch, options);
}
