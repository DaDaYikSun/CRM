import { useId } from "react";
import styles from "./Field.module.css";

// CMP-U09 Select (컴포넌트 설계 3-2). 정해진 선택지에서만 고른다 (POL-REG-002). props는 제안 (#145에서 추가).
export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectProps {
  label: string;
  required?: boolean;
  options: SelectOption[];
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  error?: string;
  disabled?: boolean;
  /** 아무것도 고르지 않았을 때 보이는 첫 줄 */
  placeholder?: string;
}

export function Select({ label, required, options, value, onChange, onBlur, error, disabled, placeholder }: SelectProps) {
  const id = useId();
  const messageId = `${id}-message`;
  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
        {required && <span className={styles.required}> *</span>}
      </label>
      <select
        id={id}
        className={`${styles.control} ${error ? styles.invalid : ""}`}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onBlur={onBlur}
        disabled={disabled}
        aria-required={required || undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? messageId : undefined}
      >
        <option value="">{placeholder ?? ""}</option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error && (
        <p id={messageId} className={styles.error}>
          {error}
        </p>
      )}
    </div>
  );
}
