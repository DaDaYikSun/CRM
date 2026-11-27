"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ApiError, LOAD_FAILED_MESSAGE } from "@/lib/api/client";
import { getTargetSummary, getTargets, type TargetLine, type TargetSummary, type TargetTab } from "@/lib/api/target";
import { todayKst } from "@/lib/utils/date";
import { TargetRow } from "@/features/target/TargetRow";
import styles from "@/features/target/target.module.css";
import { ContactPanel } from "@/features/contact/ContactPanel";
import contactStyles from "@/features/contact/contact.module.css";
import type { PageData } from "@/types/common";

// S-03 연락 대상 목록 (FR-TGT-001 ~ 003). 문구는 정책 4-2 · 4-4, 배치는 화면 설계 SCR-S03.
// 오늘 · 이번 주는 서버가 한국 시간으로 판단한다 (POL-TGT-001). ?tab= 으로 탭을 연다 (대시보드 카드 #156).
// 줄을 누르면 목록을 그대로 두고 오른쪽에 연락 패널(S-04, #153)이 열린다. 저장하면 목록 · 탭 숫자를 다시 부른다.
const GUIDE = "예정일이 된 아이를 보호자 단위로 묶었습니다. 줄을 누르면 오른쪽에서 연락 결과를 남깁니다.";
const EMPTY: Record<TargetTab, string> = {
  today: "오늘 연락할 아이가 없습니다. 이번 주 탭에서 미리 연락할 수 있습니다.",
  week: "이번 주 연락할 아이가 없습니다.",
  missed: "누락된 아이가 없습니다.",
};
const TABS: { value: TargetTab; label: string }[] = [
  { value: "today", label: "오늘" },
  { value: "week", label: "이번 주(월~일)" },
  { value: "missed", label: "누락" },
];

function parseTab(raw: string | null): TargetTab {
  return raw === "week" || raw === "missed" ? raw : "today";
}

function tabCount(summary: TargetSummary | null, tab: TargetTab): number | null {
  if (!summary) return null;
  return tab === "missed" ? summary.missed : summary[tab].total;
}

function Targets() {
  const params = useSearchParams();
  const tab = parseTab(params.get("tab"));
  const page = Math.max(1, Number(params.get("page")) || 1);
  const [data, setData] = useState<PageData<TargetLine> | null>(null);
  const [summary, setSummary] = useState<TargetSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [reload, setReload] = useState(0);
  const today = todayKst();

  useEffect(() => {
    let stale = false;
    setError(null);
    getTargets(tab, page)
      .then((list) => !stale && setData(list))
      .catch((e: unknown) => {
        if (stale) return;
        setData(null);
        // 권한 없음(403)은 서버 문구 그대로 (T10 · T11), 그 밖은 조회 실패 (POL-ERR-001)
        setError(e instanceof ApiError && e.status === 403 ? e.message : LOAD_FAILED_MESSAGE);
      });
    // 탭 숫자만 쓰므로 요약이 실패해도 목록은 보인다 (숫자만 비움)
    getTargetSummary()
      .then((sum) => !stale && setSummary(sum))
      .catch(() => !stale && setSummary(null));
    return () => {
      stale = true;
    };
  }, [tab, page, reload]);

  const href = (t: TargetTab, p = 1) => `/targets?tab=${t}${p > 1 ? `&page=${p}` : ""}`;
  // 마지막보다 큰 페이지는 items가 비고 total만 온다 (K16 ⑥). 범위는 받은 줄이 있을 때만 보인다
  const hasRows = !!data && data.items.length > 0;
  const from = hasRows ? (data.page - 1) * data.size + 1 : 0;
  const to = hasRows ? from + data.items.length - 1 : 0;

  const list = (
    <section>
      <h1>연락 대상</h1>
      <p className={styles.guide}>{GUIDE}</p>
      <nav className={styles.tabs} aria-label="연락 대상 탭">
        {TABS.map((t) => {
          const count = tabCount(summary, t.value);
          return (
            <Link
              key={t.value}
              href={href(t.value)}
              className={styles.tab}
              aria-current={t.value === tab ? "page" : undefined}
            >
              {t.label}
              {count !== null && ` ${count}`}
            </Link>
          );
        })}
      </nav>
      {error && <p className={styles.error}>{error}</p>}
      {data && !hasRows && data.total === 0 && <p className={styles.empty}>{EMPTY[tab]}</p>}
      {data && hasRows && (
        <>
          <p className={styles.sortNote}>예정일이 오래 지난 순</p>
          <div className={styles.list}>
            {data.items.map((line) => (
              <TargetRow
                key={line.guardianId}
                line={line}
                today={today}
                selected={selected === line.guardianId}
                onClick={() => setSelected(line.guardianId)}
              />
            ))}
          </div>
        </>
      )}
      {data && data.total > 0 && (
        <nav className={styles.pager} aria-label="페이지">
          {hasRows && (
            <span>
              {from}–{to} / {data.total}가구 · {data.size}개씩
            </span>
          )}
          {data.page > 1 && <Link href={href(tab, Math.min(data.page - 1, Math.ceil(data.total / data.size)))}>이전</Link>}
          {hasRows && to < data.total && <Link href={href(tab, data.page + 1)}>다음</Link>}
        </nav>
      )}
    </section>
  );

  if (selected === null) return list;
  return (
    <div className={contactStyles.split}>
      {list}
      <ContactPanel guardianId={selected} onSaved={() => setReload((n) => n + 1)} onClose={() => setSelected(null)} />
    </div>
  );
}

export default function TargetsPage() {
  return (
    <Suspense>
      <Targets />
    </Suspense>
  );
}
