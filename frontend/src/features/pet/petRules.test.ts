import { describe, expect, it } from "vitest";
import { PET_COPY, lastPage, recordedTime, scheduleBadge } from "./petRules";

describe("scheduleBadge", () => {
  it("저장된 누락이면 날짜와 상관없이 누락", () => {
    expect(scheduleBadge(true, "2026-12-30", "2026-11-27")).toBe("missed");
  });
  it("누락이 아니고 예정일이 지났으면 예정 지남", () => {
    expect(scheduleBadge(false, "2026-11-26", "2026-11-27")).toBe("late");
  });
  it("오늘이거나 앞이면 배지 없음", () => {
    expect(scheduleBadge(false, "2026-11-27", "2026-11-27")).toBeNull();
    expect(scheduleBadge(false, "2026-12-01", "2026-11-27")).toBeNull();
  });
});

describe("표시 규칙", () => {
  it("시간은 HH:mm", () => {
    expect(recordedTime("2026-11-24T10:12:00+09:00")).toBe("10:12");
  });
  it("마지막 쪽은 최소 1", () => {
    expect(lastPage(0, 20)).toBe(1);
    expect(lastPage(21, 20)).toBe(2);
  });
  it("상태 배지 글자는 정책 4-2 그대로", () => {
    expect([PET_COPY.active, PET_COPY.birthEstimated, PET_COPY.late, PET_COPY.missed, PET_COPY.converted]).toEqual([
      "재방문 관리 중",
      "생년월일 추정",
      "예정 지남",
      "누락",
      "전환",
    ]);
  });
});
