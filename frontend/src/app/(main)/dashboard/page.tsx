"use client";

import { useEffect, useRef, useState } from "react";
import { ErrorState } from "@/components/ui/ErrorState";
import { PageGuide } from "@/components/ui/PageGuide";
import { loadFailure, type Failure } from "@/lib/api/failure";
import { getTargetSummary, type TargetSummary } from "@/lib/api/target";
import { KST_TIME_ZONE } from "@/lib/utils/date";
import { MetricsSection } from "@/features/dashboard/MetricsSection";
import { StatCard } from "@/features/dashboard/StatCard";
import { CARD_LABEL, TODAY_CARD_TABS, targetsHref, todayCards } from "@/features/dashboard/todayCards";
import styles from "@/features/dashboard/dashboard.module.css";

// S-02 대시보드 (FR-DASH-001, 화면 설계 SCR-S02). 관리자 · 데스크의 로그인 뒤 첫 화면 (POL-ACC-007).
// 미용사는 middleware.ts가 권한 없음 화면을 보여 주고, 서버 summary도 403이다 (T3, NFR-SEC-002).
// ① 오늘 할 일 카드(#156), ② 재방문 지표 · ③ 연락 효과(#157, MetricsSection). ④ 번호 오류 모음은 초안 20이 이 아래에 더한다.
// ①과 ②③은 따로 불러서, 한쪽이 실패해도 다른 쪽은 보인다.
const LOADING = "숫자를 불러오는 중입니다."; // 정책 4-2 '숫자 불러오는 중 (S-02)'

const todayLabel = new Intl.DateTimeFormat("ko-KR", { timeZone: KST_TIME_ZONE, dateStyle: "full" });

export default function DashboardPage() {
  const [summary, setSummary] = useState<TargetSummary | null>(null);
  const [error, setError] = useState<Failure | null>(null);
  const [attempt, setAttempt] = useState(0);
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    let stale = false;
    setSummary(null);
    setError(null);
    // 조회 실패는 api.get이 자동으로 한 번 다시 부른다 (POL-ERR-001)
    getTargetSummary()
      .then((s) => !stale && setSummary(s))
      .catch((e: unknown) => {
        if (stale) return;
        // 권한 없음(403)은 서버 문구 그대로 (T11), 그 밖은 조회 실패 + [다시 시도] (#168)
        setError(loadFailure(e));
      });
    return () => {
      stale = true;
    };
  }, [attempt]);

  return (
    <section>
      <h1 ref={heading} tabIndex={-1}>
        대시보드
      </h1>
      <PageGuide screen="S-02" />
      <p className={styles.date}>{todayLabel.format(new Date())}</p>

      {error ? (
        <ErrorState
          message={error.message}
          onRetry={
            error.retry
              ? () => {
                  // 누르면 이 버튼이 사라지므로 포커스를 제목으로 옮겨 잃지 않게 한다
                  heading.current?.focus();
                  setAttempt((n) => n + 1);
                }
              : undefined
          }
        />
      ) : (
        <>
          <div className={styles.cards} aria-busy={summary === null}>
            {summary
              ? todayCards(summary).map((c) => (
                  <StatCard key={c.tab} label={c.label} value={c.value} href={targetsHref(c.tab)} linkLabel={c.linkLabel} />
                ))
              : TODAY_CARD_TABS.map((tab) => <StatCard key={tab} label={CARD_LABEL[tab]} loading />)}
          </div>
          {summary === null && (
            <p className={styles.status} role="status">
              {LOADING}
            </p>
          )}
        </>
      )}
      <MetricsSection />
    </section>
  );
}
