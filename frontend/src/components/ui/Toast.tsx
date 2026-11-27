"use client";

import { useEffect } from "react";
import styles from "./Notice.module.css";

// CMP-U15 Toast (컴포넌트 설계 3-2). 저장 뒤 잠깐 뜨는 알림 (3초). props는 제안 (#145에서 추가).
// 문구는 정책 4-2 '저장 · 완료 안내'를 그대로 넘긴다.
export const TOAST_DURATION_MS = 3000;

export interface ToastProps {
  message: string;
  tone?: "success" | "info";
  onClose: () => void;
}

export function Toast({ message, tone = "success", onClose }: ToastProps) {
  useEffect(() => {
    const timer = setTimeout(onClose, TOAST_DURATION_MS);
    return () => clearTimeout(timer);
  }, [message, onClose]);

  return (
    <div className={`${styles.toast} ${styles[tone]}`} role="status" aria-live="polite">
      {message}
    </div>
  );
}
