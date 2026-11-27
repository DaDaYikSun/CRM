import type { ReactNode } from "react";
import styles from "./Notice.module.css";

// CMP-U16 Notice (컴포넌트 설계 3-2). 화면 안 알림 상자. props는 제안 (#145에서 추가).
export interface NoticeProps {
  tone: "danger" | "warning" | "info";
  children: ReactNode;
  actions?: ReactNode;
}

export function Notice({ tone, children, actions }: NoticeProps) {
  return (
    <div className={`${styles.notice} ${styles[tone]}`} role={tone === "danger" ? "alert" : "status"}>
      <div className={styles.body}>{children}</div>
      {actions && <div className={styles.actions}>{actions}</div>}
    </div>
  );
}
