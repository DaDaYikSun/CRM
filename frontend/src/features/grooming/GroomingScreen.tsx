"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Badge } from "@/components/ui/Badge";
import { ErrorState } from "@/components/ui/ErrorState";
import { PageGuide } from "@/components/ui/PageGuide";
import { TextField } from "@/components/ui/TextField";
import { Toast } from "@/components/ui/Toast";
import { loadFailure, type Failure } from "@/lib/api/failure";
import { getGrooming, type GroomingLine, type GroomingTab } from "@/lib/api/grooming";
import { todayKst } from "@/lib/utils/date";
import { ageLabel } from "@/features/search/age";
import type { PageData, Role } from "@/types/common";
import { GroomingPanel } from "./GroomingPanel";
import { GROOMING_COPY, TABS, filterLines, groomingBadge, type GroomingBadge } from "./groomingRules";
import styles from "./grooming.module.css";

const BADGE_TONE: Record<NonNullable<GroomingBadge>, "brand" | "warning" | "danger" | "success"> = {
  today: "brand",
  late: "warning",
  missed: "danger",
  done: "success",
};

function parseTab(raw: string | null): GroomingTab {
  return raw === "week" ? "week" : "today";
}

// S-11 미용 (FR-VIS-002, 화면 설계 SCR-S11, #155). 미용사의 로그인 뒤 첫 화면이고 데스크 · 관리자도 연다 (POL-ACC-007 · 011).
// 오늘 · 이번 주 기준과 정렬은 서버가 한국 시간으로 정한다 (연락 대상과 같음). 오늘 미용한 아이는 '미용 완료'로 목록 아래에 남는다.
// 줄을 누르면 오른쪽에 선택한 아이. 처음에는 첫 아이를 고른다 (SCR-S11-03). 기록하면 목록 · 탭 숫자를 다시 부른다.
// '아이 찾기'는 지금 보이는 쪽 안에서 아이 이름 · 보호자 이름 · 번호 뒤 4자리로 거른다. 병원 전체 검색은 위쪽 검색창(S-05).
export function GroomingScreen({ role }: { role: Role }) {
  const params = useSearchParams();
  const tab = parseTab(params.get("tab"));
  const page = Math.max(1, Number(params.get("page")) || 1);
  const [data, setData] = useState<PageData<GroomingLine> | null>(null);
  const [counts, setCounts] = useState<Partial<Record<GroomingTab, number>>>({});
  const [error, setError] = useState<Failure | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [reload, setReload] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const [query, setQuery] = useState("");
  const [toast, setToast] = useState<string | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const today = todayKst();

  // 탭 · 쪽이 바뀌면 이전 줄과 선택을 비운다. 기록 뒤 다시 부를 때(reload)는 비우지 않아 목록이 깜빡이지 않는다
  useEffect(() => {
    setData(null);
    setSelected(null);
  }, [tab, page]);

  useEffect(() => {
    let stale = false;
    setError(null);
    const other: GroomingTab = tab === "today" ? "week" : "today";
    getGrooming(tab, page)
      .then((list) => {
        if (stale) return;
        setData(list);
        setCounts((c) => ({ ...c, [tab]: list.total }));
        // 처음에는 첫 아이를 고른다. 고른 아이가 이 쪽에 없으면 그대로 둔다 (기록 뒤 '미용 완료'로 남아 있음)
        setSelected((s) => s ?? list.items[0]?.petId ?? null);
      })
      .catch((e: unknown) => {
        if (stale) return;
        setData(null);
        setError(loadFailure(e));
      });
    // 다른 탭 숫자만 쓰므로 실패하면 숫자만 비운다
    getGrooming(other, 1)
      .then((list) => !stale && setCounts((c) => ({ ...c, [other]: list.total })))
      .catch(() => !stale && setCounts((c) => ({ ...c, [other]: undefined })));
    return () => {
      stale = true;
    };
  }, [tab, page, reload, attempt]);

  const retry = () => {
    heading.current?.focus(); // [다시 시도]가 사라져도 포커스를 잃지 않게 (PR #213 리뷰)
    setAttempt((n) => n + 1);
  };
  const closeToast = useCallback(() => setToast(null), []);
  const onSaved = useCallback((message: string) => {
    setToast(message);
    setReload((n) => n + 1);
  }, []);

  const href = (t: GroomingTab, p = 1) => `/grooming?tab=${t}${p > 1 ? `&page=${p}` : ""}`;
  const hasRows = !!data && data.items.length > 0;
  const rows = data ? filterLines(data.items, query) : [];
  const from = hasRows ? (data.page - 1) * data.size + 1 : 0;
  const to = hasRows ? from + data.items.length - 1 : 0;

  return (
    <div className={selected !== null ? styles.split : undefined}>
      <section aria-label={GROOMING_COPY.listTitle}>
        <h1 ref={heading} tabIndex={-1}>
          {GROOMING_COPY.title}
        </h1>
        <PageGuide screen="S-11" />
        <nav className={styles.tabs} aria-label="미용 예정 탭">
          {TABS.map((t) => (
            <Link key={t.value} href={href(t.value)} className={styles.tab} aria-current={t.value === tab ? "page" : undefined}>
              {t.label}
              {counts[t.value] !== undefined && ` ${counts[t.value]}`}
            </Link>
          ))}
        </nav>
        {error && <ErrorState message={error.message} onRetry={error.retry ? retry : undefined} />}
        {data && !hasRows && data.total === 0 && <p className={styles.empty}>{GROOMING_COPY.empty[tab]}</p>}
        {hasRows && (
          <>
            <div className={styles.find}>
              <TextField label="아이 찾기" type="search" value={query} onChange={setQuery}
                hint="아이 이름 · 보호자 이름 · 번호 뒤 4자리" />
            </div>
            <p className={styles.sortNote}>예정일이 오래 지난 순 · 오늘 미용한 아이는 아래</p>
            <ul className={styles.list}>
              {rows.map((line) => {
                const badge = groomingBadge(line, today);
                return (
                  <li key={line.petId}>
                    <button
                      type="button"
                      className={`${styles.row} ${line.groomedToday ? styles.done : ""}`}
                      aria-pressed={selected === line.petId}
                      onClick={() => setSelected(line.petId)}
                    >
                      <span className={styles.rowMain}>
                        <strong>{line.petName}</strong>
                        <span className={styles.meta}>
                          {line.breedName} · {line.coatTypeName} · {ageLabel(line.birthDate, today)} · {line.guardianName} ({line.phoneLast4})
                        </span>
                      </span>
                      {line.dueDate && <span className={styles.due}>{line.dueDate}</span>}
                      {badge && <Badge tone={BADGE_TONE[badge]}>{GROOMING_COPY.badge[badge]}</Badge>}
                    </button>
                  </li>
                );
              })}
            </ul>
          </>
        )}
        {data && data.total > 0 && (
          <nav className={styles.pager} aria-label="페이지">
            {hasRows && (
              <span>
                {from}–{to} / {data.total}마리 · {data.size}개씩
              </span>
            )}
            {data.page > 1 && <Link href={href(tab, Math.min(data.page - 1, Math.ceil(data.total / data.size)))}>이전</Link>}
            {hasRows && to < data.total && <Link href={href(tab, data.page + 1)}>다음</Link>}
          </nav>
        )}
      </section>
      {/* key: 아이가 바뀌면 새로 만들어 이전 아이의 값 · 늦게 끝난 조회가 섞이지 않게 한다 */}
      {selected !== null && <GroomingPanel key={selected} petId={selected} role={role} onSaved={onSaved} />}
      {toast && <Toast message={toast} onClose={closeToast} />}
    </div>
  );
}
