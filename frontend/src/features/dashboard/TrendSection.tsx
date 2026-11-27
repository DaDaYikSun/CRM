"use client";

import { ErrorState } from "@/components/ui/ErrorState";
import { getTrends, type VisitBuckets } from "@/lib/api/dashboard";
import { useQueryWithRetry } from "@/lib/hooks/useQueryWithRetry";
import { METRICS_COPY } from "./metrics";
import type { Period } from "./period";
import {
  BUCKETS,
  TREND_SERIES,
  hasTrendData,
  linePath,
  seriesRates,
  weekLabel,
  xOf,
  yOf,
  type Box,
} from "./trendChart";
import styles from "./dashboard.module.css";

// S-02 재방문율 추이 · 예정일 대비 실제 방문 분포 (FR-DASH-007 · 008, #185). 기간은 위 기간 선택과 같다 (#176).
// 추이는 한 주(월~일) 단위, 대상이 없는 주는 선을 끊는다. 목표선은 넣지 않는다 (FR-DASH-007). 그래프는 SVG로 직접 (#185 결정).

const LINE: Box = { width: 640, height: 220, pad: 28 };
const BAR = { width: 640, height: 200, pad: 28 };
const SERVICES: { key: "vaccine" | "grooming"; label: string; color: string }[] = [
  { key: "vaccine", label: "접종 (주요 접종)", color: "var(--peri-600)" },
  { key: "grooming", label: "미용", color: "var(--peri-300)" },
];

function pct(rate: number | null): string {
  return rate === null ? METRICS_COPY.empty : `${Math.round(rate * 1000) / 10}%`;
}

export function TrendSection({ period }: { period: Period }) {
  const { data, failure, loading, retry } = useQueryWithRetry(() => getTrends(period.from, period.to), [
    period.from,
    period.to,
  ]);

  if (failure && !data) {
    return (
      <section className={styles.section} aria-labelledby="trend-heading">
        <h2 id="trend-heading" className={styles.sectionTitle}>
          재방문율 추이
        </h2>
        <ErrorState message={failure.message} onRetry={failure.retry ? retry : undefined} retrying={loading} />
      </section>
    );
  }
  if (!data) return null; // 지표 카드가 이미 불러오는 중을 보여 준다

  if (!hasTrendData(data.weeks, data.distribution)) {
    return (
      <section className={styles.section} aria-labelledby="trend-heading">
        <h2 id="trend-heading" className={styles.sectionTitle}>
          재방문율 추이
        </h2>
        <p className={styles.empty}>{METRICS_COPY.empty}</p>
      </section>
    );
  }

  const weeks = data.weeks;
  const max = Math.max(1, ...SERVICES.flatMap((s) => BUCKETS.map((b) => data.distribution[s.key][b.key])));
  const groupWidth = (BAR.width - 2 * BAR.pad) / BUCKETS.length;
  const barWidth = Math.min(36, groupWidth / 3);

  return (
    <>
      <section className={styles.section} aria-labelledby="trend-heading" aria-busy={loading}>
        <h2 id="trend-heading" className={styles.sectionTitle}>
          재방문율 추이
        </h2>
        <ul className={styles.legend}>
          {TREND_SERIES.map((s) => (
            <li key={s.key}>
              <span className={styles.swatch} style={{ background: s.color }} aria-hidden="true" />
              {s.label}
            </li>
          ))}
        </ul>
        <svg className={styles.chart} viewBox={`0 0 ${LINE.width} ${LINE.height}`} role="img" aria-labelledby="trend-heading">
          {[0, 0.5, 1].map((r) => (
            <g key={r}>
              <line x1={LINE.pad} x2={LINE.width - LINE.pad} y1={yOf(r, LINE)} y2={yOf(r, LINE)} className={styles.gridLine} />
              <text x={4} y={yOf(r, LINE) + 4} className={styles.axisText}>
                {r * 100}%
              </text>
            </g>
          ))}
          {weeks.map((w, i) => (
            <text key={w.weekStart} x={xOf(i, weeks.length, LINE)} y={LINE.height - 6} textAnchor="middle" className={styles.axisText}>
              {weekLabel(w.weekStart)}
            </text>
          ))}
          {TREND_SERIES.map((s) => {
            const rates = seriesRates(weeks, s.key);
            return (
              <g key={s.key}>
                <path d={linePath(rates, LINE)} fill="none" stroke={s.color} strokeWidth={2}
                  strokeDasharray={"dashed" in s && s.dashed ? "5 4" : undefined} />
                {rates.map((rate, i) =>
                  rate === null ? null : (
                    <circle key={i} cx={xOf(i, weeks.length, LINE)} cy={yOf(rate, LINE)} r={3} fill={s.color}>
                      <title>{`${weeks[i].weekStart} 주 ${s.label} ${pct(rate)}`}</title>
                    </circle>
                  ),
                )}
              </g>
            );
          })}
        </svg>
      </section>

      <section className={styles.section} aria-labelledby="distribution-heading">
        <h2 id="distribution-heading" className={styles.sectionTitle}>
          예정일 대비 실제 방문 분포
        </h2>
        <ul className={styles.legend}>
          {SERVICES.map((s) => (
            <li key={s.key}>
              <span className={styles.swatch} style={{ background: s.color }} aria-hidden="true" />
              {s.label}
            </li>
          ))}
        </ul>
        <svg className={styles.chart} viewBox={`0 0 ${BAR.width} ${BAR.height + 24}`} aria-hidden="true">
          <line x1={BAR.pad} x2={BAR.width - BAR.pad} y1={BAR.height} y2={BAR.height} className={styles.gridLine} />
          {BUCKETS.map((b, i) => {
            const center = BAR.pad + groupWidth * (i + 0.5);
            return (
              <g key={b.key}>
                {SERVICES.map((s, j) => {
                  const count = data.distribution[s.key][b.key];
                  const h = (count / max) * (BAR.height - BAR.pad);
                  const x = center + (j - 1) * barWidth + (j === 0 ? -2 : 2);
                  return (
                    <g key={s.key}>
                      <rect x={x} y={BAR.height - h} width={barWidth} height={h} fill={s.color} rx={3} />
                      <text x={x + barWidth / 2} y={BAR.height - h - 4} textAnchor="middle" className={styles.axisText}>
                        {count}
                      </text>
                    </g>
                  );
                })}
                <text x={center} y={BAR.height + 18} textAnchor="middle" className={styles.axisText}>
                  {b.label}
                </text>
              </g>
            );
          })}
        </svg>
        {/* 화면 읽기 프로그램 · 손계산 대조용으로 같은 숫자를 표로도 둔다 */}
        <table className={styles.table}>
          <caption className={styles.caption}>주기 수</caption>
          <thead>
            <tr>
              <th scope="col">구분</th>
              {BUCKETS.map((b) => (
                <th key={b.key} scope="col">
                  {b.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {SERVICES.map((s) => (
              <tr key={s.key}>
                <th scope="row">{s.label}</th>
                {BUCKETS.map((b) => (
                  <td key={b.key}>{(data.distribution[s.key] as VisitBuckets)[b.key]}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </>
  );
}
