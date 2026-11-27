// S-07 아이 상세의 문구와 표시 규칙 (FR-VIS-003, FR-CUS-003). 문구는 정책 정의서 4-2 · 4-4 · 상태 배지 표 그대로다.
import type { TimelineLine, TimelineType } from "@/lib/api/pet";

export const PET_COPY = {
  active: "재방문 관리 중",
  rainbow: "무지개다리",
  birthEstimated: "생년월일 추정",
  late: "예정 지남",
  missed: "누락",
  converted: "전환",
  timelineEmpty: "아직 기록이 없습니다. 접종이나 미용을 기록하면 여기에 쌓입니다.",
  timelineEmptyFiltered: "이 종류의 기록이 없습니다. 필터를 '전체'로 바꾸면 다른 기록을 볼 수 있습니다.",
  timelineFooter: "20개씩 보여줍니다 · 모든 기록에 기록한 직원과 시간이 자동으로 붙습니다.",
} as const;

export const FILTERS: { value: TimelineType; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "VACCINE", label: "접종" },
  { value: "GROOMING", label: "미용" },
  { value: "CONTACT", label: "연락" },
];

export const KIND_LABEL: Record<TimelineLine["kind"], string> = { VACCINE: "접종", GROOMING: "미용", CONTACT: "연락" };

export type ScheduleBadge = "missed" | "late" | null;

/** 누락은 저장된 판정(missed)만 본다 (K17 ②, T13). '예정 지남'은 누락이 아니고 예정일이 지난 것만 (정책 4-2 상태 배지) */
export function scheduleBadge(missed: boolean, dueDate: string, today: string): ScheduleBadge {
  if (missed) return "missed";
  return dueDate < today ? "late" : null;
}

/** 'YYYY-MM-DDTHH:mm:ss+09:00' → 'HH:mm' (서버가 한국 시간으로 준다, API 설계 1-4) */
export function recordedTime(recordedAt: string): string {
  return recordedAt.slice(11, 16);
}

export function lastPage(total: number, size: number): number {
  return Math.max(1, Math.ceil(total / size));
}

/** 이름 옆에 위험 색으로 보일 주의 태그 (연락을 막는 태그) */
export const SEVERE_TAG = "NO_CONTACT";
