import type { ReactNode } from "react";
import styles from "./Badge.module.css";

// CMP-U02 Badge (컴포넌트 설계 3-2). 값별 tone은 컴포넌트 설계 4-3. 글자 크기는 12px 하나 (7장 5번).
// onClick이 있으면 누를 수 있는 배지('수정됨' → 이력).
export interface BadgeProps {
  tone?: "neutral" | "brand" | "warning" | "danger" | "success";
  children: ReactNode;
  onClick?: () => void;
}

export function Badge({ tone = "neutral", children, onClick }: BadgeProps) {
  const className = `${styles.badge} ${styles[tone]}`;
  if (onClick) {
    return (
      <button type="button" className={`${className} ${styles.clickable}`} onClick={onClick}>
        {children}
      </button>
    );
  }
  return <span className={className}>{children}</span>;
}
