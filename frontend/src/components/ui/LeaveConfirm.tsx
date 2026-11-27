"use client";

import { useRef } from "react";
import { useDialogFocus } from "@/lib/hooks/useDialogFocus";
import { Button } from "./Button";
import styles from "./LeaveConfirm.module.css";

/** 정책 4-2 '나가기 경고' */
export const LEAVE_MESSAGE = "입력한 내용이 사라집니다.";

// CMP-S07 LeaveConfirm (컴포넌트 설계 4장, FR-COM-003, #184). 입력 중에 저장하지 않고 나가려 하면 뜨는 확인 창.
// 버튼 이름 [계속 입력] [나가기]는 컴포넌트 설계의 제안. 열면 [계속 입력]에 포커스(실수로 Enter를 눌러도 입력이 남게), Esc는 계속 입력.
// 보통은 직접 쓰지 않고 useLeaveConfirm 훅이 그려 준다.
export interface LeaveConfirmProps {
  open: boolean;
  onStay: () => void;
  onLeave: () => void;
}

export function LeaveConfirm({ open, onStay, onLeave }: LeaveConfirmProps) {
  const dialog = useRef<HTMLDivElement>(null);
  useDialogFocus(dialog, open, onStay);
  if (!open) return null;
  return (
    <div className={styles.backdrop}>
      <div ref={dialog} className={styles.dialog} role="alertdialog" aria-modal="true" aria-describedby="leave-message">
        <p id="leave-message" className={styles.message}>
          {LEAVE_MESSAGE}
        </p>
        <div className={styles.actions}>
          <Button variant="secondary" onClick={onStay}>
            계속 입력
          </Button>
          <Button variant="danger" onClick={onLeave}>
            나가기
          </Button>
        </div>
      </div>
    </div>
  );
}
