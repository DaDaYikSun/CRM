"use client";

import type { ContactResult } from "@/lib/api/contact";
import { RESULT_LABEL, RESULTS } from "./contactRules";
import styles from "./contact.module.css";

// CMP ResultButton (컴포넌트 설계): 연락 결과 5가지 버튼 묶음 (POL-CNT-002). 고른 버튼은 aria-pressed.
export function ResultButtons({
  label,
  value,
  onChange,
}: {
  label: string;
  value: ContactResult | undefined;
  onChange: (result: ContactResult) => void;
}) {
  return (
    <div role="group" aria-label={label} className={styles.results}>
      {RESULTS.map((r) => (
        <button
          key={r}
          type="button"
          className={styles.result}
          aria-pressed={value === r}
          onClick={() => onChange(r)}
        >
          {RESULT_LABEL[r]}
        </button>
      ))}
    </div>
  );
}
