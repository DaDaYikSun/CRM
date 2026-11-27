import { describe, expect, it } from "vitest";
import type { TrendWeek } from "@/lib/api/dashboard";
import { hasTrendData, linePath, xOf, yOf } from "./trendChart";

const box = { width: 100, height: 100, pad: 10 };
const empty = { early: 0, late0to7: 0, late8plus: 0, missed: 0 };
const ratio = (rate: number | null, n = 0, d = 0) => ({ rate, numerator: n, denominator: d });
const week = (d: number): TrendWeek => ({
  weekStart: "2026-11-02",
  vaccineRevisit: ratio(d ? 1 : null, d, d),
  groomingRevisit: ratio(null),
  vaccineMiss: ratio(d ? 0 : null, 0, d),
});

describe("재방문율 추이 좌표 (FR-DASH-007, #185)", () => {
  it("x는 양 끝 여백 사이에 고르게, 한 점이면 가운데", () => {
    expect(xOf(0, 3, box)).toBe(10);
    expect(xOf(2, 3, box)).toBe(90);
    expect(xOf(0, 1, box)).toBe(50);
  });

  it("y는 100%가 위, 0%가 아래", () => {
    expect(yOf(1, box)).toBe(10);
    expect(yOf(0, box)).toBe(90);
  });

  it("대상이 없는 주(null)에서 선을 끊는다", () => {
    expect(linePath([1, 0.5, null, 0], box)).toBe("M10.0,10.0 L36.7,50.0 M90.0,90.0");
    expect(linePath([null, null], box)).toBe("");
  });

  it("기록이 하나도 없으면 빈 상태", () => {
    expect(hasTrendData([week(0), week(0)], { vaccine: empty, grooming: empty })).toBe(false);
    expect(hasTrendData([week(0), week(3)], { vaccine: empty, grooming: empty })).toBe(true);
    expect(hasTrendData([], { vaccine: { ...empty, missed: 1 }, grooming: empty })).toBe(true);
  });
});
