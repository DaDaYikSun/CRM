import { describe, expect, it } from "vitest";
import { isHeld, rainbowConfirmMessage } from "./rainbowRules";

describe("무지개다리 표시 문구와 보류 판단", () => {
  it("문구의 보류 기간은 받은 기준값이다", () => {
    expect(rainbowConfirmMessage("보리", 45)).toBe(
      "보리를 무지개다리로 표시하면 모든 연락 대상과 통계에서 빠집니다. 같은 집 다른 아이는 45일 동안 연락을 보류합니다.",
    );
  });

  it("마감 당일까지 보류이고 다음 날부터 아니다", () => {
    expect(isHeld("2026-12-24", "2026-12-24")).toBe(true);
    expect(isHeld("2026-12-24", "2026-12-25")).toBe(false);
    expect(isHeld(null, "2026-12-24")).toBe(false);
  });
});
