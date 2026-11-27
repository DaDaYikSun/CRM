// S-02 대시보드 기간 선택 (FR-DASH-005, #176). 날짜는 한국 날짜 'YYYY-MM-DD' 글자 그대로 다룬다.
// - 이번 달: 이번 달 1일 ~ 오늘
// - 지난 3개월: 이번 달을 넣어 3개월. 두 달 전 1일 ~ 오늘 (#176 결정: 이번 달 포함)
// - 직접 선택: 오늘까지만 고른다. 오늘 뒤는 서버도 세지 않는다 (#176 결정, API 설계 3-8)

export type PeriodKind = "month" | "quarter" | "custom";

export const PERIOD_OPTIONS: { value: PeriodKind; label: string }[] = [
  { value: "month", label: "이번 달" },
  { value: "quarter", label: "지난 3개월" },
  { value: "custom", label: "직접 선택" },
];

/** 정책 4-4 S-02 '기간 선택' */
export const PERIOD_GUIDE = "페이지를 옮기지 않고 다시 계산합니다";

/** 칸 이름으로 만들 수 없는 입력 오류의 공통 문구 (API 설계 7장 18번, 정해지기 전 임시) */
export const PERIOD_INVALID = "입력한 값을 확인해 주세요.";

export interface Period {
  from: string;
  to: string;
}

/** today('YYYY-MM-DD')가 있는 달에서 monthsBack달 전의 1일 */
function firstOfMonth(today: string, monthsBack: number): string {
  const [y, m] = today.split("-").map(Number);
  const index = y * 12 + (m - 1) - monthsBack;
  const year = Math.floor(index / 12);
  const month = (index % 12) + 1;
  return `${year}-${String(month).padStart(2, "0")}-01`;
}

/** 이번 달 · 지난 3개월의 기간 */
export function presetPeriod(kind: Exclude<PeriodKind, "custom">, today: string): Period {
  return { from: firstOfMonth(today, kind === "month" ? 0 : 2), to: today };
}

/** 직접 선택 입력이 틀렸으면 문구, 맞으면 null. 비었거나 · 시작 > 끝 · 오늘 뒤면 틀림 */
export function customPeriodError(period: Period, today: string): string | null {
  const { from, to } = period;
  if (!from || !to) return PERIOD_INVALID;
  // 'YYYY-MM-DD'는 글자 비교가 날짜 비교와 같다
  if (from > to || to > today) return PERIOD_INVALID;
  return null;
}
