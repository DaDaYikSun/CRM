"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { TextField } from "@/components/ui/TextField";
import { getMetrics, type Metrics } from "@/lib/api/dashboard";
import { loadFailure, type Failure } from "@/lib/api/failure";
import { todayKst } from "@/lib/utils/date";
import { METRICS_COPY, hasContacts, methodRows, resultRows, revisitCards, type ConversionRow } from "./metrics";
import { TrendSection } from "./TrendSection";
import { PERIOD_GUIDE, PERIOD_OPTIONS, customPeriodError, presetPeriod, type Period, type PeriodKind } from "./period";
import styles from "./dashboard.module.css";

// S-02 ② 재방문 지표 · ③ 연락 효과 (FR-DASH-002 · 003, 화면 설계 SCR-S02-02 · 03, #157).
// 기간은 처음 '이번 달'. 이번 달 / 지난 3개월 / 직접 선택으로 바꾸면 페이지 이동 없이 다시 계산한다 (FR-DASH-005, #176).
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
  // 렌더할 때마다 다시 읽는다: 화면을 연 채로 자정을 넘겨도 날짜 칸의 max가 새 날짜를 쓴다 (PR #255 리뷰)
  const today = todayKst();
  const [kind, setKind] = useState<PeriodKind>("month");
  const [period, setPeriod] = useState<Period>(() => presetPeriod("month", todayKst()));
  const [draft, setDraft] = useState<Period>(period);
  const [draftError, setDraftError] = useState<string | null>(null);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [error, setError] = useState<Failure | null>(null);
  const [attempt, setAttempt] = useState(0);
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    let stale = false;
    setMetrics(null);
    setError(null);
    getMetrics(period.from, period.to)
      .then((m) => !stale && setMetrics(m))
      .catch((e: unknown) => {
        if (!stale) setError(loadFailure(e)); // 권한 없음(403)은 서버 문구 그대로 (T11)
      });
    return () => {
      stale = true;
    };
  }, [attempt, period]);

  function choose(next: PeriodKind) {
    setKind(next);
    setDraftError(null);
    if (next === "custom") {
      setDraft(period); // 지금 보고 있는 기간에서 고치기 시작한다
    } else {
      setPeriod(presetPeriod(next, todayKst())); // 누른 순간의 한국 날짜
    }
  }

  function applyCustom(event: FormEvent) {
    event.preventDefault();
    const error = customPeriodError(draft, todayKst());
    setDraftError(error);
    if (!error) setPeriod({ ...draft });
  }

  return (
    <>
      <section className={styles.section} aria-labelledby="revisit-heading">
        <h2 id="revisit-heading" ref={heading} tabIndex={-1} className={styles.sectionTitle}>
          재방문 지표
        </h2>
        <div className={styles.periodPicker}>
          <SegmentedControl label="기간" options={PERIOD_OPTIONS} value={kind} onChange={choose} />
          <span className={styles.periodGuide}>{PERIOD_GUIDE}</span>
        </div>
        {kind === "custom" && (
          <form className={styles.periodForm} onSubmit={applyCustom} noValidate>
            <TextField
              label="시작일"
              type="date"
              max={today}
              value={draft.from}
              onChange={(from) => setDraft((d) => ({ ...d, from }))}
              error={draftError ?? undefined}
            />
            <TextField
              label="종료일"
              type="date"
              max={today}
              value={draft.to}
              onChange={(to) => setDraft((d) => ({ ...d, to }))}
            />
            <Button type="submit" variant="secondary">
              적용
            </Button>
          </form>
        )}
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

      <TrendSection period={period} />
    </>
  );
}
