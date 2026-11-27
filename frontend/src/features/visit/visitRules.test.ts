import { describe, expect, it } from "vitest";
import { canSave, savedMessage } from "./visitRules";

const TODAY = "2026-11-25";

describe("canSave", () => {
  it("접종은 종류가 있어야 저장된다", () => {
    expect(canSave("VACCINE", "", TODAY, "", TODAY)).toBe(false);
    expect(canSave("VACCINE", "1", TODAY, "", TODAY)).toBe(true);
  });

  it("미용은 종류 없이 저장된다", () => {
    expect(canSave("GROOMING", "", TODAY, "", TODAY)).toBe(true);
  });

  it("날짜가 비거나 오늘 뒤면 저장할 수 없다", () => {
    expect(canSave("GROOMING", "", "", "", TODAY)).toBe(false);
    expect(canSave("GROOMING", "", "2026-11-26", "", TODAY)).toBe(false);
    expect(canSave("GROOMING", "", "2026-11-24", "", TODAY)).toBe(true);
  });

  it("메모는 200자까지", () => {
    expect(canSave("GROOMING", "", TODAY, "가".repeat(201), TODAY)).toBe(false);
    expect(canSave("GROOMING", "", TODAY, "가".repeat(200), TODAY)).toBe(true);
  });
});

describe("savedMessage", () => {
  it("정책 4-2 문구 그대로", () => {
    expect(savedMessage("VACCINE")).toBe("접종 기록을 저장했습니다. 다음 예정일을 다시 계산했습니다.");
    expect(savedMessage("GROOMING")).toBe("미용 기록을 저장했습니다. 다음 예정일을 다시 계산했습니다.");
  });
});
