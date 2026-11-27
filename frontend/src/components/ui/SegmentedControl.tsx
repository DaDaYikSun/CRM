import type { KeyboardEvent } from "react";
import styles from "./Field.module.css";

// CMP-U11 SegmentedControl (컴포넌트 설계 3-2). 몇 개 중 값 하나 고르기. props는 제안 (#145에서 추가).
export interface SegmentedOption<T extends string> {
  value: T;
  label: string;
  disabled?: boolean;
}

export interface SegmentedControlProps<T extends string> {
  /** 묶음 이름 (화면 읽기용) */
  label: string;
  options: SegmentedOption<T>[];
  value: T;
  onChange: (value: T) => void;
  disabled?: boolean;
}

// event.key는 string이라 문자열 키로 읽는다 (strict에서 인덱싱 오류 방지)
const ARROW_STEP: Record<string, number | undefined> = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };

export function SegmentedControl<T extends string>({
  label,
  options,
  value,
  onChange,
  disabled,
}: SegmentedControlProps<T>) {
  // 라디오 패턴: 방향키로 이동(비활성 건너뜀 · 순환), 선택한 항목만 탭 순서에 둔다
  function move(event: KeyboardEvent<HTMLDivElement>) {
    const step = ARROW_STEP[event.key];
    if (!step || disabled) return;
    const enabled = options.filter((o) => !o.disabled);
    const current = enabled.findIndex((o) => o.value === value);
    const next = enabled[(current + step + enabled.length) % enabled.length];
    if (!next) return;
    event.preventDefault();
    onChange(next.value);
    const buttons = event.currentTarget.querySelectorAll<HTMLButtonElement>("button:not(:disabled)");
    buttons[enabled.indexOf(next)]?.focus();
  }

  return (
    <div role="radiogroup" aria-label={label} className={styles.segmented} onKeyDown={move}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={option.value === value}
          tabIndex={option.value === value ? 0 : -1}
          className={`${styles.segment} ${option.value === value ? styles.segmentSelected : ""}`}
          disabled={disabled || option.disabled}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
