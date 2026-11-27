"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { getContactHistory } from "@/lib/api/contact";
import { useQueryWithRetry } from "@/lib/hooks/useQueryWithRetry";
import {
  CONTACT_HISTORY_EMPTY,
  CONTACT_HISTORY_TITLE,
  lastPage,
  pageToShowInstead,
} from "./contactHistoryRules";
import styles from "./ContactHistoryTable.module.css";
import cardStyles from "./GuardianDetail.module.css";

// S-06 연락 기록 표 (#158, FR-CNT-001). 최근 순 20개씩. 미용사는 서버가 403으로 막는다 (POL-ACC-011, NFR-SEC-002).
// 조회 실패는 공통 장치(#168): 네트워크 · 서버 오류는 [다시 시도], 권한 없음 같은 4xx는 서버 문구만.
export function ContactHistoryTable({ guardianId }: { guardianId: number }) {
  const [page, setPage] = useState(1);
  const { data, failure, loading, retry } = useQueryWithRetry(() => getContactHistory(guardianId, page), [guardianId, page]);

  // 마지막 쪽을 넘어선 쪽이면 마지막 쪽으로 옮긴다
  useEffect(() => {
    const instead = data ? pageToShowInstead(data) : null;
    if (instead !== null) setPage(instead);
  }, [data]);

  const last = data ? lastPage(data.total, data.size) : 1;
  // 다음 쪽을 부르는 동안에는 이전 쪽 기록이 그대로 보이므로 쪽 번호도 지금 보이는 응답의 것을 쓴다
  const shown = data?.page ?? page;

  return (
    <section className={cardStyles.card} aria-label={CONTACT_HISTORY_TITLE} aria-busy={loading}>
      <h2 className={cardStyles.sectionTitle}>{CONTACT_HISTORY_TITLE}</h2>
      {failure && <ErrorState message={failure.message} onRetry={failure.retry ? retry : undefined} retrying={loading} />}
      {/* 첫 조회 중: 연락 기록 로딩 문구는 정책에 없어 글자 없이 자리만 둔다 (문구를 지어내지 않는다) */}
      {!failure && !data && <div className={styles.skeleton} aria-hidden="true" />}
      {!failure && data && data.total === 0 && <p className={styles.empty}>{CONTACT_HISTORY_EMPTY}</p>}
      {!failure && data && data.total > 0 && (
        <>
          <div className={styles.scroll}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>날짜</th>
                  <th>아이</th>
                  <th>방법 · 통화한 사람 · 결과</th>
                  <th>메모</th>
                  <th>기록 직원</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((c) => (
                  <tr key={c.id}>
                    <td>{c.date}</td>
                    <td>{c.petName}</td>
                    <td>{c.summary}</td>
                    <td className={styles.memo}>{c.memo ?? ""}</td>
                    <td>{c.staffName}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {last > 1 && (
            <div className={styles.pager}>
              <Button variant="ghost" size="sm" disabled={loading || shown <= 1} onClick={() => setPage(shown - 1)}>이전</Button>
              <span>{shown} / {last}</span>
              <Button variant="ghost" size="sm" disabled={loading || shown >= last} onClick={() => setPage(shown + 1)}>다음</Button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
