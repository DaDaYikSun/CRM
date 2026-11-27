// S-06 보호자 상세 · S-07 프로필 수정의 문구와 입력 규칙 (FR-CUS-008). 문구는 정책 정의서 4-2 · 4-4 그대로다.

import type { GuardianPatch, GuardianTagCode } from "@/lib/api/customer";
import { normalizePhone } from "@/lib/utils/phone";

export const GUARDIAN_COPY = {
  subtitle: "연락처와 주의 태그, 같은 집 아이를 관리합니다. 전체 번호는 이 화면과 연락 패널에서만 보입니다.",
  phoneChangeNote: "번호를 바꾸면 번호 확인 날짜가 오늘로 바뀝니다.",
  noContactNote: "'연락 원치 않음'이 붙어 있으면 이 보호자는 연락 대상 목록에 나오지 않습니다.",
  rainbowAdminOnly: "되돌리기는 관리자만 할 수 있습니다.",
  petProfileNote: "아이 프로필을 바꾸면 예정일을 다시 계산합니다",
} as const;

export const GUARDIAN_SAVED_TOAST = "보호자 정보를 저장했습니다.";
export const PET_SAVED_TOAST = "아이 정보를 저장했습니다. 바뀐 항목에 따라 예정일을 다시 계산했습니다.";
export const REVERTED_TOAST = "무지개다리 표시를 되돌렸습니다.";
export const FORBIDDEN_MESSAGE = "이 작업을 할 권한이 없습니다. 필요하면 관리자에게 문의해 주세요.";

/** 정책 4-2 '무지개다리 되돌리기' */
export function revertConfirmMessage(petName: string): string {
  return `${petName}의 무지개다리 표시를 되돌리면 연락 대상과 통계에 다시 들어가고, 이 아이 때문에 생긴 같은 집 연락 보류가 풀립니다.`;
}

/** 정책 4장 '연락 보류' */
export function holdLabel(until: string): string {
  return `${until}까지 연락 보류`;
}

export const TAG_ORDER: GuardianTagCode[] = ["NO_CONTACT", "SMS_ONLY", "SENSITIVE", "CONTACT_TIME"];

export const TAG_LABELS: Record<GuardianTagCode, string> = {
  NO_CONTACT: "연락 원치 않음",
  SMS_ONLY: "문자만",
  SENSITIVE: "예민함",
  CONTACT_TIME: "연락 선호 시간",
};

/** '연락 선호 시간' 메모 상한 (DB contact_time_note VARCHAR(50), FR-CUS-008) */
export const CONTACT_TIME_NOTE_MAX = 50;

export function toggleTag(tags: GuardianTagCode[], tag: GuardianTagCode): GuardianTagCode[] {
  const next = tags.includes(tag) ? tags.filter((t) => t !== tag) : [...tags, tag];
  return TAG_ORDER.filter((t) => next.includes(t));
}

export interface GuardianFormState {
  name: string;
  phone: string;
  subPhone: string;
  preferredContact: "PHONE" | "SMS" | "";
  tags: GuardianTagCode[];
  contactTimeNote: string;
}

/** 저장된 값과 다른 칸만 담는다. 바뀐 것이 없으면 null (저장 요청을 보내지 않는다) */
export function buildGuardianPatch(
  saved: GuardianFormState,
  form: GuardianFormState,
): GuardianPatch | null {
  const patch: GuardianPatch = {};
  if (form.name.trim() !== saved.name) patch.name = form.name.trim();
  if (normalizePhone(form.phone) !== normalizePhone(saved.phone)) patch.phone = normalizePhone(form.phone);
  if (normalizePhone(form.subPhone) !== normalizePhone(saved.subPhone)) {
    patch.subPhone = normalizePhone(form.subPhone) || null;
  }
  if (form.preferredContact !== saved.preferredContact) patch.preferredContact = form.preferredContact || null;
  if (form.tags.join() !== saved.tags.join()) patch.tags = form.tags;
  const note = form.tags.includes("CONTACT_TIME") ? form.contactTimeNote.trim() : "";
  const savedNote = saved.tags.includes("CONTACT_TIME") ? saved.contactTimeNote : "";
  if (note !== savedNote) patch.contactTimeNote = note || null;
  return Object.keys(patch).length > 0 ? patch : null;
}

/** 저장 전 화면 검사. 서버도 같은 검사를 한다 (NFR-SEC-002 · POL-REG-001) */
export function guardianFormErrors(form: GuardianFormState): Partial<Record<"name" | "phone" | "subPhone", string>> {
  const errors: Partial<Record<"name" | "phone" | "subPhone", string>> = {};
  if (!form.name.trim()) errors.name = "보호자 이름을(를) 입력해 주세요.";
  if (!/^\d{9,11}$/.test(normalizePhone(form.phone))) errors.phone = "입력한 값을 확인해 주세요.";
  const sub = normalizePhone(form.subPhone);
  if (sub && !/^\d{9,11}$/.test(sub)) errors.subPhone = "입력한 값을 확인해 주세요.";
  return errors;
}

/** 체중 칸 → 숫자. 비어 있으면 undefined(그대로), 0 이하 · 소수 둘째 자리 이상 · 999.9 초과는 null(오류) */
export function parseWeight(input: string): number | undefined | null {
  const text = input.trim();
  if (!text) return undefined;
  if (!/^\d{1,3}(\.\d)?$/.test(text)) return null;
  const value = Number(text);
  return value > 0 ? value : null;
}

/** 전체 번호 표시용 (010-0000-0000) */
export function formatPhone(digits: string): string {
  if (digits.length === 11) return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
  if (digits.length === 10) return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
  return digits;
}
