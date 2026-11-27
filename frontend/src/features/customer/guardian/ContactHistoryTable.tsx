"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Notice } from "@/components/ui/Notice";
import { FORBIDDEN_MESSAGE } from "@/features/auth/access";
import { ApiError, LOAD_FAILED_MESSAGE } from "@/lib/api/client";
import type { PageData } from "@/types/common";
import { fetchGuardianContacts } from "./contactHistoryApi";
import {
  CONTACT_HISTORY_EMPTY,
  CONTACT_HISTORY_TITLE,
  lastPage,
  pageToShowInstead,
  type GuardianContactItem,
} from "./contactHistoryRules";
import styles from "./ContactHistoryTable.module.css";
import cardStyles from "./GuardianDetail.module.css";

// S-06 연락 기록 표 (#158, FR-CNT-001). 최근 순 20개씩. 미용사는 서버가 403으로 막는다 (POL-ACC-011, NFR-SEC-002).
export function ContactHistoryTable({ guardianId }: { guardianId: number }) {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<PageData<GuardianContactItem> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let stale = false;
    fetchGuardianContacts(guardianId, page)
      .then((d) => {
        if (stale) return;
        const instead = pageToShowInstead(d);
        if (instead !== null) setPage(instead);
        else {
          setError(null);
          setData(d);
        }
      })
      .catch((e: unknown) => {
        if (stale) return;
        setError(e instanceof ApiError ? (e.code === "FORBIDDEN" ? FORBIDDEN_MESSAGE : e.message) : LOAD_FAILED_MESSAGE);
      });
    return () => {
      stale = true;
    };
  }, [guardianId, page]);

  const last = data ? lastPage(data.total, data.size) : 1;

  return (
    <section className={cardStyles.card} aria-label={CONTACT_HISTORY_TITLE}>
      <h2 className={cardStyles.sectionTitle}>{CONTACT_HISTORY_TITLE}</h2>
      {error && <Notice tone="danger">{error}</Notice>}
      {!error && data && data.total === 0 && <p className={styles.empty}>{CONTACT_HISTORY_EMPTY}</p>}
      {!error && data && data.total > 0 && (
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
              <Button variant="ghost" size="sm" disabled={page <= 1} onClick={() => setPage(page - 1)}>이전</Button>
              <span>{page} / {last}</span>
              <Button variant="ghost" size="sm" disabled={page >= last} onClick={() => setPage(page + 1)}>다음</Button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
