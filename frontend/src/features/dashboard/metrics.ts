// S-02 재방문 지표 · 연락 효과 (FR-DASH-002 · 003, 화면 설계 SCR-S02-02 ② · ③). 숫자는 서버가 센 값 그대로 쓴다 (K17).
// 화면 문구는 정책 4-2 · 4-4 글자 그대로. 그리기와 떼어 테스트한다.
import type { ContactMethod, Conversion, Metrics, Ratio } from "@/lib/api/dashboard";
import { RESULT_LABEL, RESULTS } from "@/features/contact/contactRules";

export const METRICS_COPY = {
  empty: "아직 기록이 없습니다.", // 정책 4-2 · POL-ERR-008 공통 빈 상태
  loading: "숫자를 불러오는 중입니다.", // 정책 4-2 '숫자 불러오는 중 (S-02)'. 지표 카드 · 추이(#185)가 같이 쓴다
  methodNote: "전화 안 받음 2일차 뒤 보낸 안내 문자도 문자로 셉니다.", // 정책 4-4 S-02 '연락 방법별 전환'
} as const;

export interface MetricCard {
  label: string;
  value: string | null; // null이면 빈 상태 문구
  detail: string | null;
}

/**
 * 천분의 몇으로 반올림 (0.5 올림). 분자 · 분모 정수로 계산한다: 1001/2000을 소수로 계산하면 500.4999…가 되어
 * 50%로 내려간다 (PR #241 리뷰). 서버 rate(소수 넷째 자리)를 다시 반올림하지 않고 분자 · 분모에서 바로 낸다
 */
export function permille(count: number, total: number): number {
  return Math.floor((2 * count * 1000 + total) / (2 * total));
}

/** 천분의 몇 → "62.1%" */
function pctText(perMille: number): string {
  return `${perMille / 10}%`;
}

/**
 * 재방문율과 누락률은 같은 대상의 나머지라 합이 100%로 보여야 한다 (POL-MISS-003 · 004).
 * 누락률을 표시 자릿수로 반올림하고 재방문율은 100에서 뺀다 (따로 반올림하면 98.8% + 1.3% = 100.1%, PR #241 리뷰).
 */
export function complementPercents(missed: number, total: number): { revisit: string; miss: string } {
  const miss = permille(missed, total);
  return { revisit: pctText(1000 - miss), miss: pctText(miss) };
}

function card(label: string, r: Ratio, value: string, unit: string): MetricCard {
  if (r.rate === null) return { label, value: null, detail: null };
  return { label, value, detail: `${r.numerator}${unit} / ${r.denominator}${unit}` };
}

/** 6.4 → "+6.4일", -1.2 → "-1.2일", 0 → "0일" */
export function gapText(days: number): string {
  return `${days > 0 ? "+" : ""}${days}일`;
}

/** ② 지표 4개 (SCR-S02-02 순서). 비율은 아이 수 기준 (POL-MISS-003 · 009) */
export function revisitCards(m: Metrics): MetricCard[] {
  const { vaccine, grooming } = m.avgGapDays;
  const gap = [vaccine !== null ? `접종 ${gapText(vaccine)}` : null, grooming !== null ? `미용 ${gapText(grooming)}` : null]
    .filter(Boolean)
    .join(" · ");
  const v = m.vaccineMissRate;
  const vaccinePct = v.denominator ? complementPercents(v.numerator, v.denominator) : null;
  const g = m.revisitRate.grooming;
  const groomingPct = g.denominator ? complementPercents(g.denominator - g.numerator, g.denominator) : null;
  return [
    card("접종 재방문율 (주요 접종)", m.revisitRate.vaccine, vaccinePct?.revisit ?? "", "마리"),
    card("미용 재방문율", g, groomingPct?.revisit ?? "", "마리"),
    card("접종 누락률 (주요 접종)", v, vaccinePct?.miss ?? "", "마리"),
    { label: "예정일 대비 실제 방문 차이", value: gap || null, detail: gap ? "평균, + 는 예정일보다 늦게 옴" : null },
  ];
}

export interface ConversionRow {
  label: string;
  rate: string | null; // null이면 빈 상태 문구
  count: string;
}

function row(label: string, c: Conversion): ConversionRow {
  return { label, rate: c.total === 0 ? null : pctText(permille(c.converted, c.total)), count: `${c.converted}건 / ${c.total}건` };
}

/** ③ 결과 유형별 (5가지 고정 순서, NFR-DATA-003) */
export function resultRows(m: Metrics): ConversionRow[] {
  return RESULTS.map((r) => row(RESULT_LABEL[r], m.conversionByResult[r]));
}

const METHOD_LABEL: Record<ContactMethod, string> = { PHONE: "전화", SMS: "문자 (모의 발송)" };

/** ③ 연락 방법별 */
export function methodRows(m: Metrics): ConversionRow[] {
  return (Object.keys(METHOD_LABEL) as ContactMethod[]).map((k) => row(METHOD_LABEL[k], m.conversionByMethod[k]));
}

/** 연락이 한 건도 없으면 표 대신 빈 상태 */
export function hasContacts(m: Metrics): boolean {
  return m.conversionByMethod.PHONE.total + m.conversionByMethod.SMS.total > 0;
}
