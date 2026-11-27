// S-08 보호자 · 아이 등록 폼 규칙 (#145). 화면 그리기와 떼어 테스트한다.
// 근거: FR-CUS-001 · 002, POL-REG-001~005 · 010, POL-ERR-003, 정책 4-2 · 4-4, API 설계 3-2 · 3-3.

import type { BreedOption, PetInput, RegisterGuardianInput } from "@/lib/api/customer";
import { todayKst } from "@/lib/utils/date";
import { normalizePhone } from "@/lib/utils/phone";

/** 정책 4-4 S-08 화면별 안내 문구 (글자 그대로) */
export const REGISTER_COPY = {
  intro:
    "필수 6칸만 채우면 저장할 수 있습니다. 나머지는 나중에 채웁니다. 보호자 정보는 보호자 상세에서, 아이 정보는 아이 상세에서 고칩니다.",
  approxAge: "1살 미만은 개월 수, 그 이상은 나이로 받습니다. 오늘에서 뺀 날을 생년월일로 쓰고 '추정' 표시를 붙입니다.",
  options: "품종·털 유형은 정해진 선택지에서만 고릅니다. 목록은 백오피스에서 관리합니다.",
  smsConsent: "동의하지 않으면 문자 보내기 버튼이 꺼집니다. 전화 연락은 할 수 있습니다.",
  afterSave: "첫 접종·미용을 기록하면 예정일이 다시 계산됩니다.",
} as const;

/** 정책 4-2 '보호자 · 아이 등록 (S-08)' 저장 안내 */
export const REGISTERED_TOAST = "보호자와 아이를 등록했습니다.";

/** 정책 4-2 '같은 번호' */
export function duplicateWarning(guardianName: string, petCount: number): string {
  return `이미 등록된 보호자입니다: ${guardianName} (아이 ${petCount}마리)`;
}

/** 정책 4-2 '필수 항목' */
export function requiredMessage(label: string): string {
  return `${label}을(를) 입력해 주세요.`;
}

/** 필수 6칸 (POL-REG-001, T9). 표시 순서는 화면 순서 */
export type RequiredField = "phone" | "guardianName" | "petName" | "birth" | "breedId" | "coatTypeId";

export const REQUIRED_LABELS: Record<RequiredField, string> = {
  phone: "보호자 전화번호",
  guardianName: "보호자 이름",
  petName: "아이 이름",
  birth: "생년월일",
  breedId: "품종",
  coatTypeId: "털 유형",
};

export type AgeUnit = "months" | "years";

export interface RegisterFormState {
  phone: string;
  guardianName: string;
  privacyAgreed: boolean;
  smsAgreed: boolean;
  petName: string;
  /** '생년월일 모름'이면 대략 나이로 받는다 (POL-REG-004) */
  birthUnknown: boolean;
  birthDate: string;
  approxValue: string;
  approxUnit: AgeUnit;
  breedId: string;
  coatTypeId: string;
}

export const EMPTY_FORM: RegisterFormState = {
  phone: "",
  guardianName: "",
  privacyAgreed: false,
  smsAgreed: false,
  petName: "",
  birthUnknown: false,
  birthDate: "",
  approxValue: "",
  approxUnit: "years",
  breedId: "",
  coatTypeId: "",
};

/** 새 보호자로 등록할지, 같은 번호의 기존 보호자 아래에 아이만 붙일지 (SCR-S08-07) */
export type RegisterMode =
  | { kind: "new" }
  | { kind: "existing"; guardianId: number; guardianName: string; petCount: number };

/** 정책 4-2 값 형식 오류 문구 (서버 VALIDATION_FAILED와 같은 글자) */
export const VALUE_CHECK_MESSAGE = "입력한 값을 확인해 주세요.";

/** 서버 BirthDateResolver.MAX_YEARS와 같게 맞춘다 (문서에 위쪽 한도 없음, [?] #145 보고) */
export const MAX_APPROX_YEARS = 30;

/** 대략 나이 칸의 정수 값. 비었거나 정수가 아니면 null */
function approxNumber(value: string): number | null {
  const trimmed = value.trim();
  if (!/^-?\d+$/.test(trimmed)) return null;
  return Number(trimmed);
}

/** 생년월일은 오늘(한국 시간) 이하, 대략 나이는 개월 1~11 · 나이 1~30 (POL-REG-004). 칸이 비었으면 여기서 보지 않는다 */
export function birthInvalid(form: RegisterFormState, today: string = todayKst()): boolean {
  if (!form.birthUnknown) return form.birthDate !== "" && form.birthDate > today;
  const n = approxNumber(form.approxValue);
  if (n === null) return false;
  return form.approxUnit === "months" ? n < 1 || n > 11 : n < 1 || n > MAX_APPROX_YEARS;
}

function isFilled(form: RegisterFormState, field: RequiredField): boolean {
  switch (field) {
    case "phone":
      return normalizePhone(form.phone) !== "";
    case "guardianName":
      return form.guardianName.trim() !== "";
    case "petName":
      return form.petName.trim() !== "";
    case "birth":
      return form.birthUnknown ? approxNumber(form.approxValue) !== null : form.birthDate !== "";
    case "breedId":
      return form.breedId !== "";
    case "coatTypeId":
      return form.coatTypeId !== "";
  }
}

/**
 * 비어 있는 필수 칸 (POL-REG-001, POL-ERR-003). 하나라도 있으면 [저장]을 끈다.
 * 기존 보호자에 아이를 붙일 때는 보호자 칸을 쓰지 않는다.
 */
export function missingFields(form: RegisterFormState, mode: RegisterMode): RequiredField[] {
  const fields = (Object.keys(REQUIRED_LABELS) as RequiredField[]).filter(
    (field) => mode.kind === "new" || (field !== "phone" && field !== "guardianName"),
  );
  return fields.filter((field) => !isFilled(form, field));
}

export function canSave(form: RegisterFormState, mode: RegisterMode): boolean {
  return missingFields(form, mode).length === 0 && !birthInvalid(form);
}

/** 번호를 다 넣었을 때만 같은 번호를 확인한다 (DB: 숫자 9~11자리) */
export function shouldCheckPhone(phone: string): boolean {
  return /^\d{9,11}$/.test(normalizePhone(phone));
}

/** 품종을 고르면 그 품종의 기본 털 유형 (DB 4-4). 기본값이 없으면 null (지금 고른 털 유형을 그대로 둔다) */
export function defaultCoatTypeFor(breeds: BreedOption[], breedId: string): string | null {
  const breed = breeds.find((b) => String(b.breedId) === breedId);
  return breed?.defaultCoatTypeId != null ? String(breed.defaultCoatTypeId) : null;
}

export function toPetInput(form: RegisterFormState): PetInput {
  const approx = form.birthUnknown ? approxNumber(form.approxValue) : null;
  return {
    name: form.petName.trim(),
    birthDate: form.birthUnknown ? null : form.birthDate,
    approxAge: approx === null ? null : form.approxUnit === "months" ? { months: approx } : { years: approx },
    breedId: Number(form.breedId),
    coatTypeId: Number(form.coatTypeId),
  };
}

/** 전화번호는 숫자만 보낸다 (API 설계 1-4) */
export function toRegisterInput(form: RegisterFormState): RegisterGuardianInput {
  return {
    guardian: {
      name: form.guardianName.trim(),
      phone: normalizePhone(form.phone),
      privacyAgreed: form.privacyAgreed,
      smsAgreed: form.smsAgreed,
    },
    pet: toPetInput(form),
  };
}

/** 서버 오류 fields의 칸 이름 → 화면 칸 (API 설계 1-2) */
export function fieldFromServer(field: string): RequiredField | null {
  const map: Record<string, RequiredField> = {
    "guardian.phone": "phone",
    "guardian.name": "guardianName",
    "pet.name": "petName",
    "pet.birthDate": "birth",
    "pet.approxAge": "birth",
    "pet.breedId": "breedId",
    "pet.coatTypeId": "coatTypeId",
  };
  return map[field] ?? null;
}
