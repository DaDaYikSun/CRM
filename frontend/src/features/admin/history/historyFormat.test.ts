import { describe, expect, it } from "vitest";
import type { RuleHistoryItem } from "@/lib/api/admin";
import { historyChanges, historyTarget, historyTime } from "./historyFormat";

const base: RuleHistoryItem = {
  changeLogId: 1,
  targetType: "VACCINE_RULE",
  targetId: "7",
  targetName: "종합백신",
  before: { intervalDays: 14 },
  after: { intervalDays: 21 },
  staffName: "관리자",
  changedAt: "2026-11-27T15:04:00+09:00",
};

describe("B-06 변경 이력 표시 (FR-ADM-006, #182)", () => {
  it("대상은 종류와 이름", () => {
    expect(historyTarget(base)).toBe("접종 규칙 · 종합백신");
    expect(historyTarget({ ...base, targetType: "SETTING", targetId: "MISS_THRESHOLD_DAYS", targetName: null })).toBe(
      "누락 기준(일)",
    );
    expect(historyTarget({ ...base, targetType: "MESSAGE_TEMPLATE", targetId: "VACCINE", targetName: null })).toBe(
      "문자 문구 · VACCINE",
    );
  });

  it("바뀐 칸마다 전 → 후, 새로 만든 것은 '없음'에서", () => {
    expect(historyChanges(base)).toEqual(["간격(일): 14 → 21"]);
    expect(
      historyChanges({ ...base, targetType: "BREED", before: { name: null, isActive: null }, after: { name: "비숑", isActive: true } }),
    ).toEqual(["이름: 없음 → 비숑", "사용: 없음 → 예"]);
    expect(historyChanges({ ...base, before: null, after: { unknownField: 3 } })).toEqual(["unknownField: 없음 → 3"]);
  });

  it("시간은 분까지", () => {
    expect(historyTime(base.changedAt)).toBe("2026-11-27 15:04");
  });
});
