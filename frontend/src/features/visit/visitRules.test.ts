import { describe, expect, it } from "vitest";
import { canSave, isVisitDirty, savedMessage } from "./visitRules";

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

describe("isVisitDirty (나가기 경고, #243)", () => {
  const TODAY = "2026-11-25";
  const blank = { service: "VACCINE" as const, vaccineTypeId: "", visitDate: TODAY, memo: "" };
  it("처음 연 상태 그대로면 false (공백만 적은 메모도)", () => {
    expect(isVisitDirty(blank, "VACCINE", TODAY)).toBe(false);
    expect(isVisitDirty({ ...blank, memo: "  " }, "VACCINE", TODAY)).toBe(false);
  });
  it("구분 · 접종 종류 · 날짜 · 메모 중 하나라도 바꾸면 true", () => {
    expect(isVisitDirty({ ...blank, service: "GROOMING" }, "VACCINE", TODAY)).toBe(true);
    expect(isVisitDirty({ ...blank, vaccineTypeId: "1" }, "VACCINE", TODAY)).toBe(true);
    expect(isVisitDirty({ ...blank, visitDate: "2026-11-24" }, "VACCINE", TODAY)).toBe(true);
    expect(isVisitDirty({ ...blank, memo: "기침" }, "VACCINE", TODAY)).toBe(true);
  });
});
