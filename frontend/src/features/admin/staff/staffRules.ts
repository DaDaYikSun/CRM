// B-01 직원 관리 화면 규칙 · 문구 (#150, FR-ADM-001). 문구는 정책 4-4 · 4-2 그대로 (화면 설계 SCR-B01-04).
import type { Role } from "@/types/common";

export const STAFF_COPY = {
  /** 정책 4-4 B-01 목록 위 */
  listGuide: "직원마다 자기 계정을 씁니다. 퇴사하면 지우지 않고 비활성화합니다. 비밀번호는 8자 이상, 영문과 숫자를 섞습니다.",
  /** 정책 4-4 B-01 직원 추가 */
  addGuide: "추가하면 임시 비밀번호가 한 번 보입니다. 직원은 첫 로그인 때 새 비밀번호를 정합니다.",
  /** 정책 4-2 '임시 비밀번호 (B-01)' 모달 */
  tempPasswordGuide: "임시 비밀번호는 이 화면에서 한 번만 보입니다. 직원에게 전달하면 첫 로그인 때 새 비밀번호를 정하게 됩니다.",
} as const;

/** 정책 4-2 '비밀번호 초기화 확인 (B-01)' 확인 창 (#183) */
export function resetConfirmMessage(name: string): string {
  return `${name}의 비밀번호를 임시 비밀번호로 바꿉니다. 이 직원은 로그인해 둔 모든 기기에서 로그아웃됩니다.`;
}

/** 직원 추가 모달의 역할 순서: 데스크 / 미용사 / 관리자 (SCR-B01-02) */
export const ROLE_OPTIONS: { value: Role; label: string }[] = [
  { value: "DESK", label: "데스크" },
  { value: "GROOMER", label: "미용사" },
  { value: "ADMIN", label: "관리자" },
];

/** 상태: 사용 중 / 비활성 (SCR-B01-02) */
export function statusLabel(isActive: boolean): string {
  return isActive ? "사용 중" : "비활성";
}

/** 아이디 · 이름은 30자까지 (DB 설계 4-1 VARCHAR(30)) */
export const NAME_MAX = 30;
export const LOGIN_ID_MAX = 30;

export interface StaffForm {
  name: string;
  loginId: string;
  role: Role | "";
}

/** 필수 칸이 비면 "{항목}을(를) 입력해 주세요." (정책 4-2 '필수 항목') */
export function staffFormErrors(form: StaffForm): Partial<Record<keyof StaffForm, string>> {
  const errors: Partial<Record<keyof StaffForm, string>> = {};
  if (!form.name.trim()) errors.name = "이름을(를) 입력해 주세요.";
  if (!form.loginId.trim()) errors.loginId = "아이디을(를) 입력해 주세요.";
  if (!form.role) errors.role = "역할을(를) 입력해 주세요.";
  return errors;
}

/** 마지막 로그인 표시: '2026-11-27 09:01' (한국 시간). 없으면 '-' */
export function formatLastLogin(iso: string | null): string {
  if (!iso) return "-";
  const date = new Date(iso);
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return `${get("year")}-${get("month")}-${get("day")} ${get("hour")}:${get("minute")}`;
}

/** 새 비밀번호 조건 (POL-ACC-010). 화면은 미리 알려 주기만 하고, 서버가 다시 막는다 */
export const PASSWORD_MAX_BYTES = 72;

export function isValidPassword(password: string): boolean {
  // 72바이트(UTF-8)를 넘으면 서버(BCrypt)가 받지 못해 같은 조건 문구로 거절한다 (#150 리뷰, 서버 PasswordRule.MAX_BYTES)
  return (
    password.length >= 8 &&
    new TextEncoder().encode(password).length <= PASSWORD_MAX_BYTES &&
    /[A-Za-z]/.test(password) &&
    /[0-9]/.test(password)
  );
}
