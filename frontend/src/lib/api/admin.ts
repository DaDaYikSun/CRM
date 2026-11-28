// 백오피스 API (ADM, admin) — 관리자만 (API 설계 3-10). 공용 호출 함수 client.ts만 쓴다 (frontend/README.md).
// #150: 직원 관리 C16. 다른 이슈(초안 25 · 30 · 31 · 32 · 33)가 이 파일에 함수를 더한다.
import type { PageData, Role } from "@/types/common";
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

/** POST /api/admin/staff/{id}/reset-password — 비밀번호 초기화 (FR-ADM-007, #183). 임시 비밀번호는 이때 한 번만 온다 */
export function resetStaffPassword(staffId: number, options?: SaveOptions): Promise<{ tempPassword: string }> {
  return api.post<{ tempPassword: string }>(`/api/admin/staff/${staffId}/reset-password`, undefined, options);
}

// #175: 주기 규칙 · 기준값 C17 (FR-ADM-002 · 003 · 004, API 설계 3-10)

export interface VaccineTypeRow {
  vaccineTypeId: number;
  code: string;
  name: string;
  isMajor: boolean;
  isActive: boolean;
}

/** ageToDays · doseCount가 null이면 '끝 없음' · '계속 반복' (DB 4-7) */
export interface VaccineRuleRow {
  vaccineRuleId: number;
  phase: "PRIMARY" | "BOOSTER" | "PREVENTIVE";
  ageFromDays: number;
  ageToDays: number | null;
  intervalDays: number;
  doseCount: number | null;
  note: string | null;
}

export interface VaccineRuleGroup {
  vaccineType: VaccineTypeRow;
  rules: VaccineRuleRow[];
}

export interface CoatInterval {
  coatTypeId: number;
  name: string;
  defaultGroomWeeks: number;
}

export interface ThresholdSettings {
  missThresholdDays: number;
  rainbowHoldDays: number;
}

/** 규칙 · 간격 저장 출력: 아직 오지 않은 예정일을 다시 계산한 아이 수 */
export interface Recalculated {
  recalculatedPetCount: number;
}

export function getVaccineRules(): Promise<VaccineRuleGroup[]> {
  return api.get<VaccineRuleGroup[]>("/api/admin/vaccine-rules");
}

/** 고친 종류만 보낸다 */
export function saveVaccineRules(groups: VaccineRuleGroup[], options?: SaveOptions): Promise<Recalculated> {
  return api.put<Recalculated>("/api/admin/vaccine-rules", groups, options);
}

export function getCoatIntervals(): Promise<CoatInterval[]> {
  return api.get<CoatInterval[]>("/api/admin/coat-types/intervals");
}

export function saveCoatIntervals(items: CoatInterval[], options?: SaveOptions): Promise<Recalculated> {
  return api.put<Recalculated>("/api/admin/coat-types/intervals", items, options);
}

export function getThresholdSettings(): Promise<ThresholdSettings> {
  return api.get<ThresholdSettings>("/api/admin/settings");
}

export function saveThresholdSettings(input: ThresholdSettings, options?: SaveOptions): Promise<ThresholdSettings> {
  return api.put<ThresholdSettings>("/api/admin/settings", input, options);
}

// #182: 선택지 관리 · 설정 변경 이력 (FR-ADM-005 · 006, API 설계 3-10)

/** 숨긴 것도 온다 (isActive = false) */
export interface BreedItem {
  breedId: number;
  name: string;
  defaultCoatTypeId: number | null;
  sortOrder: number;
  isActive: boolean;
}

/** PATCH는 보낸 칸만 바꾼다. 기본 털 유형을 비우려면 clearDefaultCoatType: true */
export interface BreedInput {
  name?: string;
  defaultCoatTypeId?: number;
  clearDefaultCoatType?: boolean;
  sortOrder?: number;
  isActive?: boolean;
}

export interface CoatTypeItem {
  coatTypeId: number;
  code: string;
  name: string;
  defaultGroomWeeks: number;
  sortOrder: number;
  isActive: boolean;
}

/** 미용 기본 간격은 추가할 때만. 바꾸는 것은 B-02 주기 규칙(예정일 다시 계산) */
export interface CoatTypeInput {
  name?: string;
  defaultGroomWeeks?: number;
  sortOrder?: number;
  isActive?: boolean;
}

export interface RuleHistoryItem {
  changeLogId: number;
  targetType: string;
  targetId: string;
  /** 품종 · 털 유형 · 접종 종류 이름. 설정 · 문구 · 양식은 null */
  targetName: string | null;
  before: Record<string, unknown> | null;
  after: Record<string, unknown>;
  staffName: string;
  changedAt: string;
}

export function listBreeds(): Promise<BreedItem[]> {
  return api.get<BreedItem[]>("/api/admin/breeds");
}

export function createBreed(input: BreedInput, options?: SaveOptions): Promise<BreedItem> {
  return api.post<BreedItem>("/api/admin/breeds", input, options);
}

export function updateBreed(breedId: number, patch: BreedInput, options?: SaveOptions): Promise<BreedItem> {
  return api.patch<BreedItem>(`/api/admin/breeds/${breedId}`, patch, options);
}

export function listCoatTypes(): Promise<CoatTypeItem[]> {
  return api.get<CoatTypeItem[]>("/api/admin/coat-types");
}

export function createCoatType(input: CoatTypeInput, options?: SaveOptions): Promise<CoatTypeItem> {
  return api.post<CoatTypeItem>("/api/admin/coat-types", input, options);
}

export function updateCoatType(coatTypeId: number, patch: CoatTypeInput, options?: SaveOptions): Promise<CoatTypeItem> {
  return api.patch<CoatTypeItem>(`/api/admin/coat-types/${coatTypeId}`, patch, options);
}

export function getRuleHistory(page: number): Promise<PageData<RuleHistoryItem>> {
  return api.get<PageData<RuleHistoryItem>>(`/api/admin/rule-history?page=${page}`);
}
