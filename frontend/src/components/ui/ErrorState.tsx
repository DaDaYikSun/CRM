import { LOAD_FAILED_MESSAGE } from "@/lib/api/client";
import { Button } from "./Button";
import { Notice } from "./Notice";

// CMP-S03 ErrorState (컴포넌트 설계 4장): 실패 문구 + [다시 시도] (FR-COM-001, POL-ERR-001 · 002, #168).
// 조회 실패는 "불러오지 못했습니다. 다시 시도해 주세요.", 저장 실패는 "저장하지 못했습니다. 다시 시도해 주세요." (정책 4-2).
// onRetry가 없으면(권한 없음처럼 다시 해도 같은 실패) 문구만 보인다.
export interface ErrorStateProps {
  /** 기본은 조회 실패 문구. 저장 실패 · 서버 문구는 lib/api/failure.ts가 고른 문구를 넘긴다 */
  message?: string;
  onRetry?: () => void;
  /** 다시 시도 중이면 버튼을 잠근다 (저장 두 번 막기, POL-ERR-002) */
  retrying?: boolean;
}

export function ErrorState({ message = LOAD_FAILED_MESSAGE, onRetry, retrying = false }: ErrorStateProps) {
  return (
    <Notice
      tone="danger"
      actions={
        onRetry && (
          <Button variant="secondary" size="sm" loading={retrying} onClick={onRetry}>
            다시 시도
          </Button>
        )
      }
    >
      {message}
    </Notice>
  );
}
