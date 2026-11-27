// 보호자 · 아이 API (CUS, customer). 공용 호출 함수 client.ts만 쓴다 (frontend/README.md).
// #145: 선택지 C5 (API 설계 3-2), 번호 확인 · 등록 C6 (API 설계 3-3).
// 다른 이슈(#151 · 초안 19 · 20 · 21)가 이 파일에 함수를 더한다.

import { api, type SaveOptions } from "./client";

/** GET /api/options/breeds. defaultCoatTypeId가 있으면 털 유형을 미리 고른다 (DB 4-4) */
export interface BreedOption {
  breedId: number;
  name: string;
  defaultCoatTypeId: number | null;
}

/** GET /api/options/coat-types (단모 · 중모 · 장모 · 곱슬, T1) */
export interface CoatTypeOption {
  coatTypeId: number;
  code: string;
  name: string;
}

/** GET /api/options/vaccine-types (#148 방문 기록이 씀) */
export interface VaccineTypeOption {
  vaccineTypeId: number;
  code: string;
  name: string;
  isMajor: boolean;
}

/** GET /api/guardians/phone-check. 같은 번호 보호자가 여럿일 수 있다 (POL-REG-005) */
export interface PhoneCheckResult {
  exists: boolean;
  guardians: { guardianId: number; name: string; petCount: number }[];
}

/** 대략 나이: 1살 미만은 개월 수, 그 이상은 나이 (POL-REG-004) */
export type ApproxAge = { years: number } | { months: number };

/** 등록 입력의 아이. birthDate와 approxAge는 둘 중 하나만 (API 설계 3-3) */
export interface PetInput {
  name: string;
  birthDate: string | null;
  approxAge: ApproxAge | null;
  breedId: number;
  coatTypeId: number;
}

export interface GuardianInput {
  name: string;
  phone: string;
  privacyAgreed: boolean;
  smsAgreed: boolean;
}

export interface RegisterGuardianInput {
  guardian: GuardianInput;
  pet: PetInput;
}

export function fetchBreeds(): Promise<BreedOption[]> {
  return api.get<BreedOption[]>("/api/options/breeds");
}

export function fetchCoatTypes(): Promise<CoatTypeOption[]> {
  return api.get<CoatTypeOption[]>("/api/options/coat-types");
}

export function fetchVaccineTypes(): Promise<VaccineTypeOption[]> {
  return api.get<VaccineTypeOption[]>("/api/options/vaccine-types");
}

export function checkPhone(phone: string): Promise<PhoneCheckResult> {
  return api.get<PhoneCheckResult>(`/api/guardians/phone-check?phone=${encodeURIComponent(phone)}`);
}

/** 새 보호자와 첫 아이를 한 요청으로 (SCR-S08-06). 폼 하나에 저장 키 하나 (K15) */
export function registerGuardian(
  input: RegisterGuardianInput,
  options: SaveOptions,
): Promise<{ guardianId: number; petId: number }> {
  return api.post("/api/guardians", input, options);
}

/** 기존 보호자에 아이만 붙인다. 보호자 정보는 바꾸지 않는다 (SCR-S08-07) */
export function addPetToGuardian(
  guardianId: number,
  pet: PetInput,
  options: SaveOptions,
): Promise<{ petId: number }> {
  return api.post(`/api/guardians/${guardianId}/pets`, { pet }, options);
}

// ---- #151 보호자 상세 · 수정 · 아이 프로필 수정 · 무지개다리 되돌리기 (C10, API 설계 3-3) ----

export type GuardianTagCode = "NO_CONTACT" | "SMS_ONLY" | "SENSITIVE" | "CONTACT_TIME";

export interface NextSchedule {
  serviceType: "VACCINE" | "GROOMING";
  vaccineTypeName: string | null;
  dueDate: string;
  missed: boolean;
}

export interface GuardianDetailPet {
  petId: number;
  name: string;
  status: "ACTIVE" | "RAINBOW";
  breedName: string;
  coatTypeName: string;
  birthDate: string;
  birthDateEstimated: boolean;
  contactHoldUntil: string | null;
  nextSchedule: NextSchedule | null;
}

export interface GuardianDetail {
  guardian: {
    guardianId: number;
    name: string;
    /** 숫자만 저장된 전체 번호 (POL-DATA-002) */
    phone: string;
    subPhone: string | null;
    preferredContact: "PHONE" | "SMS" | null;
    phoneStatus: "OK" | "WRONG";
    phoneCheckedAt: string;
    privacyAgreed: boolean;
    smsAgreed: boolean;
    tags: GuardianTagCode[];
    contactTimeNote: string | null;
    createdAt: string;
    createdByName: string | null;
  };
  pets: GuardianDetailPet[];
}

/** 바꿀 칸만 보낸다. 칸을 빼면 그대로, null이면 비운다 (API 설계 3-3) */
export interface GuardianPatch {
  name?: string;
  phone?: string;
  subPhone?: string | null;
  preferredContact?: "PHONE" | "SMS" | null;
  tags?: GuardianTagCode[];
  contactTimeNote?: string | null;
}

/** 바꿀 칸만 보낸다 (체중은 지울 수 없다) */
export interface PetProfilePatch {
  name?: string;
  birthDate?: string;
  breedId?: number;
  coatTypeId?: number;
  weightKg?: number;
}

export function fetchGuardianDetail(guardianId: number): Promise<GuardianDetail> {
  return api.get<GuardianDetail>(`/api/guardians/${guardianId}`);
}

export function updateGuardian(
  guardianId: number,
  patch: GuardianPatch,
  options: SaveOptions,
): Promise<GuardianDetail> {
  return api.patch<GuardianDetail>(`/api/guardians/${guardianId}`, patch, options);
}

export function updatePetProfile(petId: number, patch: PetProfilePatch, options: SaveOptions): Promise<unknown> {
  return api.patch(`/api/pets/${petId}`, patch, options);
}

/** 관리자만 (서버가 막는다, POL-END-004) */
export function revertRainbow(petId: number, options: SaveOptions): Promise<{ releasedPetIds: number[] }> {
  return api.post(`/api/pets/${petId}/rainbow-revert`, undefined, options);
}

/** C3 현재 직원 (API 설계 3-1). #143 전에는 없을 수 있다 */
export function fetchCurrentRole(): Promise<{ role: "ADMIN" | "DESK" | "GROOMER" }> {
  return api.get<{ role: "ADMIN" | "DESK" | "GROOMER" }>("/api/auth/me");
}
