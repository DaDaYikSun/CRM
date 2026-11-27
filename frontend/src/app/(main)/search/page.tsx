"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { LOAD_FAILED_MESSAGE } from "@/lib/api/client";
import { searchPets, type SearchItem } from "@/lib/api/search";
import { SearchResultRow } from "@/features/search/SearchResultRow";
import styles from "@/features/search/search.module.css";
import type { PageData } from "@/types/common";

// S-05 통합 검색 결과 (FR-SRCH-001 · 002). 문구는 정책 4-2 · 4-4 그대로.
const GUIDE = "번호 뒤 4자리, 보호자·아이 이름, 초성으로 찾습니다. 띄어쓰기와 하이픈은 무시합니다.";
const MANY_RESULTS = "번호 뒤 4자리를 함께 넣으면 더 빨리 찾을 수 있습니다.";

function Results() {
  const params = useSearchParams();
  const q = params.get("q") ?? "";
  const page = Math.max(1, Number(params.get("page")) || 1);
  const [data, setData] = useState<PageData<SearchItem> | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let stale = false;
    setData(null);
    setFailed(false);
    if (!q.trim()) return;
    searchPets(q, page)
      .then((r) => {
        if (!stale) setData(r);
      })
      .catch(() => {
        if (!stale) setFailed(true);
      });
    return () => {
      stale = true;
    };
  }, [q, page]);

  const href = (p: number) => `/search?q=${encodeURIComponent(q)}&page=${p}`;
  const lastPage = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  return (
    <section>
      <h1>통합 검색</h1>
      <p className={styles.guide}>{GUIDE}</p>
      {failed && <p className={styles.error}>{LOAD_FAILED_MESSAGE}</p>}
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
