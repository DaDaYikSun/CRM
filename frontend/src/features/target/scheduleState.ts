// 예정 항목 상태 배지 (정책 4장 '상태 배지', 컴포넌트 설계 CMP-U20 ScheduleChip). 글자는 정책 그대로.
import type { TargetItem } from "@/lib/api/target";

export type ScheduleState = "normal" | "today" | "late" | "missed";

/** 누락은 저장된 판정(missed)만 본다. 날짜로 다시 계산하지 않는다 (T13). today는 한국 시간 'YYYY-MM-DD' */
export function scheduleState(item: TargetItem, today: string): ScheduleState {
  if (item.missed) return "missed";
  if (item.dueDate === today) return "today";
  if (item.dueDate < today) return "late";
  return "normal";
}

export function stateBadge(item: TargetItem, state: ScheduleState): string | null {
  switch (state) {
    case "missed":
      return "누락";
    case "late":
      return "예정 지남";
    case "today":
      return item.serviceType === "VACCINE" ? "오늘 접종" : "오늘 미용";
    default:
      return null;
  }
}

/** 예정 항목 이름: 접종이면 접종 종류, 미용이면 '미용' */
export function itemLabel(item: TargetItem): string {
  return item.serviceType === "VACCINE" ? (item.vaccineTypeName ?? "") : "미용";
}
