// S-11 미용의 문구와 표시 규칙 (FR-VIS-002, 화면 설계 SCR-S11). 문구는 정책 정의서 4-2 · 4-4 · 상태 배지 표 그대로다.
import type { GroomingLine, GroomingTab } from "@/lib/api/grooming";

export const GROOMING_COPY = {
  title: "미용",
  listTitle: "미용 예정 목록",
  /** 정책 4-2 빈 목록 */
  empty: {
    today: "오늘 미용 예정인 아이가 없습니다. 이번 주 탭을 확인해 주세요.",
    week: "이번 주 미용 예정인 아이가 없습니다.",
  } satisfies Record<GroomingTab, string>,
  /** 정책 4-4 S-11 미용 기록 안내 */
  recordNote: "기록하면 다음 미용 예정일을 다시 계산합니다.",
  /** 정책 4-2 상태 배지 */
  badge: { today: "오늘 미용", late: "예정 지남", missed: "누락", done: "미용 완료" },
} as const;

export const TABS: { value: GroomingTab; label: string }[] = [
  { value: "today", label: "오늘" },
  { value: "week", label: "이번 주(월~일)" },
];

export type GroomingBadge = keyof typeof GROOMING_COPY.badge | null;

/** 미용 완료(오늘 기록) > 누락(저장된 판정만, T13) > 예정 지남 > 오늘 미용 */
export function groomingBadge(line: Pick<GroomingLine, "groomedToday" | "missed" | "dueDate">, today: string): GroomingBadge {
  if (line.groomedToday) return "done";
  if (line.missed) return "missed";
  if (!line.dueDate) return null;
  if (line.dueDate < today) return "late";
  return line.dueDate === today ? "today" : null;
}

/** 개인 미용 주기의 계산 근거 (POL-CYC-002 · 003) */
export function intervalBasisLabel(basis: "DEFAULT" | "MEDIAN"): string {
  return basis === "MEDIAN" ? "최근 미용 간격의 중앙값" : "털 유형 기본 간격";
}

/**
 * 목록 안 아이 찾기: 아이 이름 · 보호자 이름 · 번호 뒤 4자리 (FR-SRCH-001 확인 방법, 이슈 #155).
 * 공백을 지우고 비교한다. 숫자만 넣으면 번호 뒤 4자리에서도 찾는다.
 */
export function filterLines(lines: GroomingLine[], query: string): GroomingLine[] {
  const q = query.replace(/\s/g, "").toLowerCase();
  if (!q) return lines;
  const digits = /^\d+$/.test(q);
  return lines.filter(
    (l) =>
      l.petName.replace(/\s/g, "").toLowerCase().includes(q) ||
      l.guardianName.replace(/\s/g, "").toLowerCase().includes(q) ||
      (digits && l.phoneLast4.includes(q)),
  );
}
