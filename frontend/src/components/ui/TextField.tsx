import { useId } from "react";
import styles from "./Field.module.css";

// CMP-U08 TextField (컴포넌트 설계 3-2). 이름은 설계 그대로, props는 제안 (#145에서 추가).
// 오류면 2px 빨강 테두리 + 칸 아래 문구 (POL-ERR-003, CMP-S06). 라벨과 입력을 for로 잇는다.
export interface TextFieldProps {
  label: string;
  required?: boolean;
  type?: "text" | "tel" | "date" | "number" | "search" | "password";
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  error?: string;
  hint?: string;
  maxLength?: number;
  autoComplete?: string;
  disabled?: boolean;
  min?: number | string;
  max?: number | string;
}

export function TextField({
  label,
  required,
  type = "text",
  value,
  onChange,
  onBlur,
  error,
  hint,
  maxLength,
  autoComplete,
  disabled,
  min,
  max,
}: TextFieldProps) {
  const id = useId();
  const messageId = `${id}-message`;
  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
        {required && <span className={styles.required}> *</span>}
      </label>
      <input
        id={id}
        className={`${styles.control} ${error ? styles.invalid : ""}`}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
        maxLength={maxLength}
        autoComplete={autoComplete}
        disabled={disabled}
        min={min}
        max={max}
        aria-required={required || undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || hint ? messageId : undefined}
      />
      {error ? (
        <p id={messageId} className={styles.error}>
          {error}
        </p>
      ) : (
        hint && (
          <p id={messageId} className={styles.hint}>
            {hint}
          </p>
        )
      )}
    </div>
  );
}
