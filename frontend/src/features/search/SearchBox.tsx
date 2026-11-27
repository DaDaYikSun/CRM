"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { LOAD_FAILED_MESSAGE } from "@/lib/api/client";
import { searchPreview, type SearchItem } from "@/lib/api/search";
import { SearchResultRow } from "./SearchResultRow";
import styles from "./search.module.css";

/** 입력을 멈추고 이 시간 뒤에 부른다. 응답까지 합쳐 0.5초 안 (NFR-PERF-003) */
const DEBOUNCE_MS = 250;

/** 정책 4-4 S-05 검색창 자리 글자 */
export const SEARCH_PLACEHOLDER = "보호자 · 아이 이름, 번호 뒤 4자리, 초성";

// 상단 검색창 (TopBar 검색 자리). 입력 중 드롭다운 미리보기, Enter → S-05.
export function SearchBox() {
  const router = useRouter();
  const [q, setQ] = useState("");
  const [items, setItems] = useState<SearchItem[]>([]);
  const [failed, setFailed] = useState(false);
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLFormElement>(null);

  useEffect(() => {
    if (!q.trim()) return;
    let stale = false;
    const timer = setTimeout(() => {
      searchPreview(q)
        .then((r) => {
          if (stale) return;
          setItems(r);
          setFailed(false);
        })
        .catch(() => {
          if (stale) return;
          setItems([]);
          setFailed(true);
        });
    }, DEBOUNCE_MS);
    return () => {
      stale = true;
      clearTimeout(timer);
    };
  }, [q]);

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!q.trim()) return;
    setOpen(false);
    router.push(`/search?q=${encodeURIComponent(q.trim())}`);
  }

  const show = open && q.trim() !== "" && (failed || items.length > 0);

  return (
    <form ref={box} className={styles.box} onSubmit={submit} role="search">
      <input
        className={styles.input}
        type="search"
        value={q}
        placeholder={SEARCH_PLACEHOLDER}
        aria-label="통합 검색"
        onChange={(e) => {
          setQ(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
      />
      {show && (
        <div className={styles.dropdown} onClick={() => setOpen(false)}>
          {failed ? (
            <p className={styles.error}>{LOAD_FAILED_MESSAGE}</p>
          ) : (
            items.map((it) => <SearchResultRow key={it.petId} item={it} compact />)
          )}
        </div>
      )}
    </form>
  );
}
