import { describe, expect, it } from "vitest";
import { customPeriodError, presetPeriod, PERIOD_INVALID } from "./period";

describe("대시보드 기간 (FR-DASH-005, #176)", () => {
  it("이번 달은 1일부터 오늘까지", () => {
    expect(presetPeriod("month", "2026-11-27")).toEqual({ from: "2026-11-01", to: "2026-11-27" });
  });

  it("지난 3개월은 이번 달을 넣어 두 달 전 1일부터 오늘까지", () => {
    expect(presetPeriod("quarter", "2026-11-27")).toEqual({ from: "2026-09-01", to: "2026-11-27" });
  });

  it("지난 3개월이 해를 넘어간다", () => {
    expect(presetPeriod("quarter", "2027-01-05")).toEqual({ from: "2026-11-01", to: "2027-01-05" });
    expect(presetPeriod("quarter", "2027-02-28")).toEqual({ from: "2026-12-01", to: "2027-02-28" });
  });

  it("직접 선택: 비었거나 시작 > 끝이거나 오늘 뒤면 막는다", () => {
    const today = "2026-11-27";
    expect(customPeriodError({ from: "", to: "2026-11-01" }, today)).toBe(PERIOD_INVALID);
    expect(customPeriodError({ from: "2026-11-10", to: "2026-11-01" }, today)).toBe(PERIOD_INVALID);
    expect(customPeriodError({ from: "2026-11-01", to: "2026-11-28" }, today)).toBe(PERIOD_INVALID);
    expect(customPeriodError({ from: "2026-06-01", to: "2026-11-27" }, today)).toBeNull();
    expect(customPeriodError({ from: "2026-11-27", to: "2026-11-27" }, today)).toBeNull();
  });
});
