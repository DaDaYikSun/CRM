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
