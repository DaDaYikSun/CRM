"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { PageGuide } from "@/components/ui/PageGuide";
import { getRuleHistory } from "@/lib/api/admin";
import { useQueryWithRetry } from "@/lib/hooks/useQueryWithRetry";
import { HISTORY_COPY, historyChanges, historyTarget, historyTime } from "./historyFormat";
import styles from "../rules/AdminForm.module.css";

// B-06 설정 변경 이력 (#182, FR-ADM-006, NFR-DATA-002). 관리자만 (admin/layout.tsx, 서버도 막는다 NFR-SEC-002).
// 접종 규칙 · 미용 간격 · 기준값 · 선택지 · 문자 문구 · 병원 정보 · 리포트 양식을 바꾸면 남은 기록을 최근 순으로 20개씩.
export function RuleHistory() {
  const [page, setPage] = useState(1);
  const { data, failure, loading, retry } = useQueryWithRetry(() => getRuleHistory(page), [page]);
  // 다음 쪽을 부르는 동안에는 이전 쪽이 그대로 보이므로 쪽 번호도 지금 보이는 응답의 것을 쓴다
  const shown = data?.page ?? page;
  const last = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  return (
    <section className={styles.page}>
      <div>
        <h1 className={styles.title}>변경 이력</h1>
        <PageGuide screen="B-06" />
      </div>

      {failure && !data ? (
        <ErrorState message={failure.message} onRetry={failure.retry ? retry : undefined} retrying={loading} />
      ) : data && data.total === 0 ? (
        <p className={styles.guide}>{HISTORY_COPY.empty}</p>
      ) : (
        <div className={styles.card} aria-busy={!data || loading}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">바꾼 때</th>
                <th scope="col">바꾼 직원</th>
                <th scope="col">대상</th>
                <th scope="col">바꾼 값 (전 → 후)</th>
              </tr>
            </thead>
            <tbody>
              {(data?.items ?? []).map((item) => (
                <tr key={item.changeLogId}>
                  <td>{historyTime(item.changedAt)}</td>
                  <td>{item.staffName}</td>
                  <td>{historyTarget(item)}</td>
                  <td className={styles.wrap}>
                    {historyChanges(item).map((line) => (
                      <div key={line}>{line}</div>
                    ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.total > data.size && (
        <div className={styles.footer}>
          <Button variant="ghost" size="sm" disabled={loading || shown <= 1} onClick={() => setPage(shown - 1)}>
            이전
          </Button>
          <span className={styles.guide}>
            {shown} / {last}
          </span>
          <Button variant="ghost" size="sm" disabled={loading || shown >= last} onClick={() => setPage(shown + 1)}>
            다음
          </Button>
        </div>
      )}
    </section>
  );
}
