"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ErrorState } from "@/components/ui/ErrorState";
import { PageGuide } from "@/components/ui/PageGuide";
import { loadFailure, type Failure } from "@/lib/api/failure";
import { searchPets, type SearchItem } from "@/lib/api/search";
import { SearchResultRow } from "@/features/search/SearchResultRow";
import styles from "@/features/search/search.module.css";
import type { PageData } from "@/types/common";

// S-05 통합 검색 결과 (FR-SRCH-001 · 002). 문구는 정책 4-2 · 4-4 그대로.
const MANY_RESULTS = "번호 뒤 4자리를 함께 넣으면 더 빨리 찾을 수 있습니다.";

function Results() {
  const params = useSearchParams();
  const q = params.get("q") ?? "";
  const page = Math.max(1, Number(params.get("page")) || 1);
  const [data, setData] = useState<PageData<SearchItem> | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [attempt, setAttempt] = useState(0);
  const heading = useRef<HTMLHeadingElement>(null);
  // [다시 시도]를 누르면 그 버튼이 사라지므로 포커스를 제목으로 옮겨 잃지 않게 한다 (PR #213 리뷰)
  const retry = () => {
    heading.current?.focus();
    setAttempt((n) => n + 1);
  };

  useEffect(() => {
    let stale = false;
    setData(null);
    setFailure(null);
    if (!q.trim()) return;
    searchPets(q, page)
      .then((r) => {
        if (!stale) setData(r);
      })
      .catch((e: unknown) => {
        if (!stale) setFailure(loadFailure(e));
      });
    return () => {
      stale = true;
    };
  }, [q, page, attempt]);

  const href = (p: number) => `/search?q=${encodeURIComponent(q)}&page=${p}`;
  const lastPage = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  return (
    <section>
      <h1 ref={heading} tabIndex={-1}>
        통합 검색
      </h1>
      <PageGuide screen="S-05" />
      {failure && (
        <ErrorState message={failure.message} onRetry={failure.retry ? retry : undefined} />
      )}
      {data && (
        <>
          <p>검색 결과 {data.total}건</p>
          {/* [?] 기준 건수는 정책에 없다. 임시로 한 페이지(20개)를 넘으면 띄운다 */}
          {data.total > data.size && <p className={styles.guide}>{MANY_RESULTS}</p>}
          <div className={styles.list}>
            {data.items.map((it) => (
              <SearchResultRow key={it.petId} item={it} />
            ))}
          </div>
          <nav className={styles.pager}>
            {page > 1 && <Link href={href(page - 1)}>이전</Link>}
            <span>
              {page} / {lastPage}
            </span>
            {page < lastPage && <Link href={href(page + 1)}>다음</Link>}
          </nav>
        </>
      )}
    </section>
  );
}

export default function SearchPage() {
  return (
    <Suspense>
      <Results />
    </Suspense>
  );
}
