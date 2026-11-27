import { describe, expect, it } from "vitest";
import { ageLabel } from "./age";

describe("ageLabel", () => {
  it("1살 미만은 개월 수", () => {
    expect(ageLabel("2026-06-15", "2026-11-26")).toBe("5개월");
    expect(ageLabel("2026-06-30", "2026-11-26")).toBe("4개월");
  });
  it("1살 이상은 살", () => {
    expect(ageLabel("2025-11-26", "2026-11-26")).toBe("1살");
    expect(ageLabel("2025-11-27", "2026-11-26")).toBe("11개월");
    expect(ageLabel("2023-05-02", "2026-11-26")).toBe("3살");
  });
});
