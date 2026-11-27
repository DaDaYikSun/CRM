import { FORBIDDEN_MESSAGE } from "./access";

// 권한 없음 안내. 문구는 정책 4-2 '권한 없음 (모든 역할 공통)' 하나만 쓴다 (T11).
export function ForbiddenNotice() {
  return (
    <div role="alert" style={{ padding: "var(--spacing-24)", color: "var(--status-danger-text)" }}>
      {FORBIDDEN_MESSAGE}
    </div>
  );
}
