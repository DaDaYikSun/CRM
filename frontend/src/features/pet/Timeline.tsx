"use client";

import { useEffect, useState } from "react";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Notice } from "@/components/ui/Notice";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { ApiError, LOAD_FAILED_MESSAGE } from "@/lib/api/client";
import { fetchPetTimeline, type TimelineLine, type TimelineType } from "@/lib/api/pet";
import type { PageData } from "@/types/common";
import { FILTERS, KIND_LABEL, PET_COPY, lastPage, recordedTime } from "./petRules";
import styles from "./pet.module.css";

function TimelineItem({ line }: { line: TimelineLine }) {
  return (
    <li className={styles.line}>
      <span>{line.date}</span>
      <span>{KIND_LABEL[line.kind]}</span>
      <span>
        {line.summary} {line.converted && <Badge tone="success">{PET_COPY.converted}</Badge>}
        {line.memo && <span className={styles.memo}>{line.memo}</span>}
      </span>
      <span className={styles.lineWho}>
        {line.staffName} · {recordedTime(line.recordedAt)}
      </span>
    </li>
  );
}

// 통합 타임라인 (FR-VIS-003, POL-DATA-008). 미용사는 필터를 숨기고 type을 보내지 않는다: 서버가 미용 줄만 준다 (POL-ACC-011).
// reloadKey가 바뀌면 다시 부른다 (기록 저장 뒤).
export function Timeline({ petId, showFilter, reloadKey = 0 }: { petId: number; showFilter: boolean; reloadKey?: number }) {
  const [type, setType] = useState<TimelineType>("ALL");
  const [page, setPage] = useState(1);
  // 응답을 조회 조건(아이 · 필터 · 쪽)에 묶는다: 조건이 바뀐 뒤 조회가 실패해도 이전 조건의 기록을 지금 것처럼 보이지 않는다
  const [loaded, setLoaded] = useState<{ key: string; data: PageData<TimelineLine> } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const key = `${petId}|${showFilter ? type : ""}|${page}`;
  const data = loaded?.key === key ? loaded.data : null;

  useEffect(() => {
    let stale = false;
    setError(null);
    fetchPetTimeline(petId, page, showFilter ? type : undefined)
      .then((r) => {
        if (!stale) setLoaded({ key, data: r });
      })
      .catch((e: unknown) => {
        if (!stale) setError(e instanceof ApiError ? e.message : LOAD_FAILED_MESSAGE);
      });
    return () => {
      stale = true;
    };
  }, [petId, type, page, reloadKey, showFilter, key]);

  const last = data ? lastPage(data.total, data.size) : 1;
  const filtered = showFilter && type !== "ALL";

  return (
    <section className={styles.card} aria-label="통합 타임라인">
      <h2 className={styles.sectionTitle}>타임라인</h2>
      {showFilter && (
        <SegmentedControl label="타임라인 필터" options={FILTERS} value={type}
          onChange={(v) => { setType(v); setPage(1); }} />
      )}
      {error && <Notice tone="danger">{error}</Notice>}
      {data && data.items.length === 0 && (
        <p className={styles.meta}>{filtered ? PET_COPY.timelineEmptyFiltered : PET_COPY.timelineEmpty}</p>
      )}
      {data && data.items.length > 0 && (
        <ul className={styles.list}>
          {data.items.map((line) => (
            <TimelineItem key={`${line.kind}-${line.id}`} line={line} />
          ))}
        </ul>
      )}
      {data && last > 1 && (
        <nav className={styles.pager}>
          <Button variant="secondary" size="sm" disabled={page <= 1} onClick={() => setPage(page - 1)}>이전</Button>
          <span>{page} / {last}</span>
          <Button variant="secondary" size="sm" disabled={page >= last} onClick={() => setPage(page + 1)}>다음</Button>
        </nav>
      )}
      <p className={styles.hint}>{PET_COPY.timelineFooter}</p>
    </section>
  );
}
