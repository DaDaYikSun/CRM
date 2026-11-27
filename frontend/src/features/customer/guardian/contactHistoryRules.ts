// S-06 연락 기록 표 (#158, FR-CNT-001). 화면 문구 · 페이지 계산만 둔다. 판단은 주인(contact)이 저장한 값을 그대로 보여 준다 (K17).
import type { PageData } from "@/types/common";

/** 정책 4-2 빈 목록: 표에 S-06 연락 기록 행이 없어 POL-ERR-008 공통 문구 */
export const CONTACT_HISTORY_EMPTY = "아직 기록이 없습니다.";

export const CONTACT_HISTORY_TITLE = "연락 기록";

export function lastPage(total: number, size: number): number {
  return Math.max(1, Math.ceil(total / size));
}

/** 서버가 범위 밖 page에 빈 items와 total을 주면, 화면은 마지막 쪽으로 옮긴다. 옮길 곳이 없으면 null (K16 ⑥) */
export function pageToShowInstead(data: PageData<unknown>): number | null {
  if (data.items.length > 0 || data.total === 0) return null;
  const last = lastPage(data.total, data.size);
  return last < data.page ? last : null;
}
