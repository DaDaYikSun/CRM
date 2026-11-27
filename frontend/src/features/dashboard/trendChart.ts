// S-02 재방문율 추이 꺾은선 · 방문 분포 막대의 좌표 계산 (FR-DASH-007 · 008, #185). 그래프 라이브러리 없이 SVG로 그린다 (#185 결정).
import type { Ratio, TrendWeek, VisitBuckets } from "@/lib/api/dashboard";

/** 꺾은선 세 줄. 이름은 지표 카드(metrics.ts)와 같다. 목표선은 없다 (FR-DASH-007) */
export const TREND_SERIES = [
  { key: "vaccineRevisit", label: "접종 재방문율 (주요 접종)", color: "var(--peri-600)" },
  { key: "groomingRevisit", label: "미용 재방문율", color: "var(--peri-400)" },
  { key: "vaccineMiss", label: "접종 누락률 (주요 접종)", color: "var(--peri-300)", dashed: true },
] as const satisfies readonly { key: keyof Omit<TrendWeek, "weekStart">; label: string; color: string; dashed?: boolean }[];

/** 막대 네 칸. 이름은 FR-DASH-008 그대로 */
export const BUCKETS: { key: keyof VisitBuckets; label: string }[] = [
  { key: "early", label: "예정일 전" },
  { key: "late0to7", label: "0~7일 늦게" },
  { key: "late8plus", label: "8일 이상 늦게(누락 기준 안)" },
  { key: "missed", label: "누락" },
];

export interface Box {
  width: number;
  height: number;
  pad: number;
}

/** i번째 주의 x 좌표 (주가 하나면 가운데) */
export function xOf(i: number, count: number, box: Box): number {
  if (count <= 1) return box.width / 2;
  return box.pad + (i * (box.width - 2 * box.pad)) / (count - 1);
}

/** 비율(0~1)의 y 좌표. 위가 100% */
export function yOf(rate: number, box: Box): number {
  return box.pad + (1 - rate) * (box.height - 2 * box.pad);
}

/**
 * 한 줄의 SVG path. rate가 null인 주(대상 없음)에서 선을 끊는다: 끊긴 뒤 첫 점은 M으로 새로 시작한다.
 * 점이 하나도 없으면 빈 글자.
 */
export function linePath(rates: (number | null)[], box: Box): string {
  let path = "";
  let pen = false;
  rates.forEach((rate, i) => {
    if (rate === null) {
      pen = false;
      return;
    }
    path += `${pen ? "L" : "M"}${xOf(i, rates.length, box).toFixed(1)},${yOf(rate, box).toFixed(1)} `;
    pen = true;
  });
  return path.trim();
}

export function seriesRates(weeks: TrendWeek[], key: (typeof TREND_SERIES)[number]["key"]): (number | null)[] {
  return weeks.map((w) => (w[key] as Ratio).rate);
}

/** 기록이 하나도 없으면 빈 상태를 보인다 (POL-ERR-008) */
export function hasTrendData(weeks: TrendWeek[], distribution: { vaccine: VisitBuckets; grooming: VisitBuckets }): boolean {
  const anyWeek = weeks.some((w) => w.vaccineRevisit.denominator > 0 || w.groomingRevisit.denominator > 0);
  const total = (b: VisitBuckets) => b.early + b.late0to7 + b.late8plus + b.missed;
  return anyWeek || total(distribution.vaccine) + total(distribution.grooming) > 0;
}

/** 'MM-DD' 축 글자 */
export function weekLabel(weekStart: string): string {
  return weekStart.slice(5);
}
