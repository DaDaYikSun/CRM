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
  return (
    <div
      role="button"
      tabIndex={0}
      className={`${styles.row} ${selected ? styles.selected : ""}`}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onClick();
        }
      }}
    >
      <div className={styles.guardian}>
        <strong>{line.guardianName}</strong>
        <span className={styles.phone}>{line.phoneLast4}</span>
      </div>
      <ul className={styles.pets}>
        {line.pets.map((pet) => (
          <li key={pet.petId} className={styles.pet}>
            <span className={styles.petName}>{pet.name}</span>
            {pet.items.map((it) => (
              <ScheduleChip key={it.scheduleId} item={it} today={today} />
            ))}
            {open && pet.moreItems.map((it) => <ScheduleChip key={it.scheduleId} item={it} today={today} />)}
          </li>
        ))}
      </ul>
      {more > 0 && (
        <button
          type="button"
          className={styles.more}
          aria-expanded={open}
          onClick={(e) => {
            e.stopPropagation();
            setOpen(!open);
          }}
        >
          {open ? "−" : `+${more}`}
        </button>
      )}
    </div>
  );
}
