"use client";

import { useEffect, useRef, useState } from "react";
import { ErrorState } from "@/components/ui/ErrorState";
import { getMetrics, type Metrics } from "@/lib/api/dashboard";
import { loadFailure, type Failure } from "@/lib/api/failure";
import { METRICS_COPY, hasContacts, methodRows, resultRows, revisitCards, type ConversionRow } from "./metrics";
import styles from "./dashboard.module.css";

// S-02 ② 재방문 지표 · ③ 연락 효과 (FR-DASH-002 · 003, 화면 설계 SCR-S02-02 · 03, #157).
// 기간은 처음 '이번 달'(서버 기본값). 기간 선택 버튼은 #176이 이 위에 더한다.
const LOADING = "숫자를 불러오는 중입니다."; // 정책 4-2 '숫자 불러오는 중 (S-02)'

function ConversionTable({ caption, rows }: { caption: string; rows: ConversionRow[] }) {
  return (
    <table className={styles.table}>
      <caption className={styles.caption}>{caption}</caption>
      <thead>
        <tr>
          <th scope="col">구분</th>
          <th scope="col">전환율</th>
          <th scope="col">전환 / 연락</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.label}>
            <th scope="row">{r.label}</th>
            <td>{r.rate ?? METRICS_COPY.empty}</td>
            <td>{r.count}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function MetricsSection() {
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [error, setError] = useState<Failure | null>(null);
  const [attempt, setAttempt] = useState(0);
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    let stale = false;
    setMetrics(null);
    setError(null);
    getMetrics()
      .then((m) => !stale && setMetrics(m))
      .catch((e: unknown) => {
        if (!stale) setError(loadFailure(e)); // 권한 없음(403)은 서버 문구 그대로 (T11)
      });
    return () => {
      stale = true;
    };
  }, [attempt]);

  return (
    <>
      <section className={styles.section} aria-labelledby="revisit-heading">
        <h2 id="revisit-heading" ref={heading} tabIndex={-1} className={styles.sectionTitle}>
          재방문 지표
        </h2>
        {metrics && (
          <p className={styles.period}>
            기간: {metrics.from} ~ {metrics.to}
          </p>
        )}
        {error ? (
          <ErrorState
            message={error.message}
            onRetry={
              error.retry
                ? () => {
                    heading.current?.focus();
                    setAttempt((n) => n + 1);
                  }
                : undefined
            }
          />
        ) : (
          <>
            <div className={styles.cards} aria-busy={metrics === null}>
              {(metrics ? revisitCards(metrics) : null)?.map((c) => (
                <div key={c.label} className={styles.card}>
                  <span className={styles.label}>{c.label}</span>
                  {c.value === null ? (
                    <span className={styles.empty}>{METRICS_COPY.empty}</span>
                  ) : (
                    <>
                      <strong className={styles.value}>{c.value}</strong>
                      {c.detail && <span className={styles.detail}>{c.detail}</span>}
                    </>
                  )}
                </div>
              )) ??
                [0, 1, 2, 3].map((i) => (
                  <div key={i} className={styles.card} aria-hidden="true">
                    <span className={styles.skeleton} />
                  </div>
                ))}
            </div>
            {metrics === null && (
              <p className={styles.status} role="status">
                {LOADING}
              </p>
            )}
          </>
        )}
      </section>

      {metrics && (
        <section className={styles.section} aria-labelledby="conversion-heading">
          <h2 id="conversion-heading" className={styles.sectionTitle}>
            연락 효과
          </h2>
          {hasContacts(metrics) ? (
            <div className={styles.tables}>
              <ConversionTable caption="연락 결과별 재방문 전환" rows={resultRows(metrics)} />
              <div>
                <ConversionTable caption="연락 방법별 전환" rows={methodRows(metrics)} />
                <p className={styles.note}>{METRICS_COPY.methodNote}</p>
              </div>
            </div>
          ) : (
            <p className={styles.empty}>{METRICS_COPY.empty}</p>
          )}
        </section>
      )}
    </>
  );
}
