import { describe, expect, it } from "vitest";
import { addDays, thisWeekKst, todayKst } from "./date";
import { normalizePhone, phoneLast4 } from "./phone";

describe("date (한국 시간)", () => {
  it("UTC로 전날 밤이어도 한국 날짜", () => {
    expect(todayKst(new Date("2026-11-25T16:00:00Z"))).toBe("2026-11-26");
  });

  it("이번 주는 월~일", () => {
    expect(thisWeekKst(new Date("2026-11-26T03:00:00Z"))).toEqual({ start: "2026-11-23", end: "2026-11-29" });
    // 한국 일요일 밤
    expect(thisWeekKst(new Date("2026-11-29T14:30:00Z"))).toEqual({ start: "2026-11-23", end: "2026-11-29" });
    // 한국 월요일 새벽 (UTC로는 일요일)
    expect(thisWeekKst(new Date("2026-11-29T15:30:00Z"))).toEqual({ start: "2026-11-30", end: "2026-12-06" });
  });

  it("날짜 더하기는 달을 넘어간다", () => {
    expect(addDays("2026-11-25", 14)).toBe("2026-12-09");
  });
});

describe("phone", () => {
  it("숫자만 남긴다", () => {
    expect(normalizePhone("010-1234 5678")).toBe("01012345678");
    expect(phoneLast4("010-0000-1234")).toBe("1234");
  });
});
