import type { ButtonHTMLAttributes } from "react";
import styles from "./Button.module.css";

// CMP-U01 Button (컴포넌트 설계 3-2). 이름은 피그마 이름(확정), props는 제안.
// loading이면 다시 누를 수 없다 (POL-ERR-002, 저장 두 번 막기는 서버에서도 한다).
export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md" | "lg";
  loading?: boolean;
}

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  disabled,
  type = "button",
  className,
  children,
  ...rest
}: ButtonProps) {
  const classes = [styles.button, styles[variant], styles[size], className].filter(Boolean).join(" ");
  return (
    <button {...rest} type={type} className={classes} disabled={disabled || loading} aria-busy={loading || undefined}>
      {children}
    </button>
  );
}
