import { describe, expect, it } from "vitest";
import { CONTACT_HISTORY_EMPTY, lastPage, pageToShowInstead } from "./contactHistoryRules";

describe("연락 기록 표 규칙", () => {
  it("빈 상태 문구는 정책 4-2 공통 문구", () => {
    expect(CONTACT_HISTORY_EMPTY).toBe("아직 기록이 없습니다.");
  });

  it("21건이면 2쪽", () => {
    expect(lastPage(21, 20)).toBe(2);
    expect(lastPage(20, 20)).toBe(1);
    expect(lastPage(0, 20)).toBe(1);
  });

  it("범위를 넘은 쪽은 마지막 쪽으로, 0건이면 그대로 빈 상태", () => {
    expect(pageToShowInstead({ items: [], page: 9, size: 20, total: 21 })).toBe(2);
    expect(pageToShowInstead({ items: [], page: 1, size: 20, total: 0 })).toBeNull();
    expect(pageToShowInstead({ items: [], page: 2, size: 20, total: 21 })).toBeNull();
  });
});
