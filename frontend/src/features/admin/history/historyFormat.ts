// B-06 설정 변경 이력 한 줄을 읽기 쉬운 글로 (FR-ADM-006, #182). 서버는 change_log의 바뀐 칸만 준다.
import type { RuleHistoryItem } from "@/lib/api/admin";

/** 정책 4-4 B-06 */
export const HISTORY_COPY = {
  empty: "아직 바꾼 설정이 없습니다.",
} as const;

const TARGET_LABEL: Record<string, string> = {
  VACCINE_RULE: "접종 규칙",
  VACCINE_TYPE: "접종 종류",
  COAT_TYPE: "털 유형",
  BREED: "품종",
  SETTING: "기준값 · 병원 정보",
  MESSAGE_TEMPLATE: "문자 문구",
  REPORT_TEMPLATE: "리포트 양식",
};

/** setting_key 이름 (DB 설계 4-8) */
const SETTING_LABEL: Record<string, string> = {
  MISS_THRESHOLD_DAYS: "누락 기준(일)",
  RAINBOW_HOLD_DAYS: "연락 보류 기간(일)",
  HOSPITAL_NAME: "병원 이름",
  HOSPITAL_PHONE: "병원 전화번호",
  CRM_START_DATE: "CRM 도입일",
};

/** 바뀐 칸 이름. 모르는 칸은 그대로 보인다 */
const FIELD_LABEL: Record<string, string> = {
  ageFromDays: "시작 나이(일)",
  ageToDays: "끝 나이(일)",
  intervalDays: "간격(일)",
  doseCount: "횟수",
  isMajor: "주요 접종",
  defaultGroomWeeks: "미용 기본 간격(주)",
  name: "이름",
  defaultCoatTypeId: "기본 털 유형",
  sortOrder: "순서",
  isActive: "사용",
  value: "값",
  setting_value: "값",
  body: "문구",
};

function show(value: unknown): string {
  if (value === null || value === undefined) return "없음";
  if (typeof value === "boolean") return value ? "예" : "아니오";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

/** '무엇을' 칸: 대상 종류 · 이름 */
export function historyTarget(item: RuleHistoryItem): string {
  const kind = TARGET_LABEL[item.targetType] ?? item.targetType;
  if (item.targetType === "SETTING") return SETTING_LABEL[item.targetId] ?? item.targetId;
  return item.targetName ? `${kind} · ${item.targetName}` : `${kind} · ${item.targetId}`;
}

/** '어떻게' 칸: 칸마다 "이름: 전 → 후" */
export function historyChanges(item: RuleHistoryItem): string[] {
  return Object.keys(item.after).map(
    (field) => `${FIELD_LABEL[field] ?? field}: ${show(item.before?.[field])} → ${show(item.after[field])}`,
  );
}

/** '언제' 칸: "2026-11-27 15:00" (서버가 +09:00으로 준다) */
export function historyTime(changedAt: string): string {
  return changedAt.slice(0, 16).replace("T", " ");
}
