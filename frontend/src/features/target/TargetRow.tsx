"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/Badge";
import type { TargetItem, TargetLine } from "@/lib/api/target";
import { itemLabel, scheduleState, stateBadge } from "./scheduleState";
import styles from "./target.module.css";

// CMP-U05 TargetRow (컴포넌트 설계 3-2): 연락 대상 목록의 보호자 단위 한 줄 (POL-TGT-011).
// 번호는 뒤 4자리만 (NFR-SEC-003). 접종은 아이마다 가장 가까운 하나 + 펼치면 나머지 (FR-TGT-002).
// 선호 연락 방법 · 주의 태그 · 다음 연락 예정일 · '처리됨'은 FR-TGT-004(#173)가 더한다.
export interface TargetRowProps {
  line: TargetLine;
  today: string;
  selected: boolean;
  onClick: () => void;
}

function ScheduleChip({ item, today }: { item: TargetItem; today: string }) {
  const state = scheduleState(item, today);
  const badge = stateBadge(item, state);
  const tone = state === "missed" ? "danger" : state === "late" ? "warning" : state === "today" ? "brand" : "neutral";
  return (
    <span className={styles.chip}>
      <span>{itemLabel(item)}</span>
      <span className={styles.due}>{item.dueDate}</span>
      {badge && <Badge tone={tone}>{badge}</Badge>}
    </span>
  );
}

export function TargetRow({ line, today, selected, onClick }: TargetRowProps) {
  const [open, setOpen] = useState(false);
  const more = line.pets.reduce((n, p) => n + p.moreItems.length, 0);
  const chipsId = `target-row-${line.guardianId}-items`;
  // 줄 선택 버튼과 펼치기 버튼을 형제로 둔다 (버튼 안에 버튼을 넣지 않음, PR #196 CodeRabbit 리뷰).
  // 버튼 안에는 phrasing 요소만 둘 수 있어 보호자 · 아이 칸은 span으로 쓴다.
  return (
    <div className={`${styles.row} ${selected ? styles.selected : ""}`}>
      <button type="button" className={styles.rowMain} aria-pressed={selected} onClick={onClick}>
        <span className={styles.guardian}>
          <strong>{line.guardianName}</strong>
          <span className={styles.phone}>{line.phoneLast4}</span>
        </span>
        <span id={chipsId} className={styles.pets}>
          {line.pets.map((pet) => (
            <span key={pet.petId} className={styles.pet}>
              <span className={styles.petName}>{pet.name}</span>
              {pet.items.map((it) => (
                <ScheduleChip key={it.scheduleId} item={it} today={today} />
              ))}
              {open && pet.moreItems.map((it) => <ScheduleChip key={it.scheduleId} item={it} today={today} />)}
            </span>
          ))}
        </span>
      </button>
      {more > 0 && (
        <button
          type="button"
          className={styles.more}
          aria-expanded={open}
          aria-controls={chipsId}
          // 화면 글자(+N · −)를 이름 앞에 그대로 두고 누구의 무엇인지 덧붙인다 (WCAG 2.5.3) [제안: 문구는 정책에 없음]
          aria-label={open ? `− ${line.guardianName} 접종 접기` : `+${more} ${line.guardianName} 접종 펼치기`}
          onClick={() => setOpen(!open)}
        >
          {open ? "−" : `+${more}`}
        </button>
      )}
    </div>
  );
}
