import { describe, expect, it } from "vitest";
import { isInvalid, toInt, toText } from "./ruleForm";

describe("ruleForm (B-02 · B-03, POL-ERR-006)", () => {
  it("비었거나 0 이하면 틀림", () => {
    expect(isInvalid("")).toBe(true);
    expect(isInvalid("  ")).toBe(true);
    expect(isInvalid("0")).toBe(true);
    expect(isInvalid("-3")).toBe(true);
    expect(isInvalid("14")).toBe(false);
  });

  it("정수가 아니면 틀림", () => {
    expect(isInvalid("1.5")).toBe(true);
    expect(isInvalid("abc")).toBe(true);
  });

  it("끝 나이 · 횟수는 비어도 된다 (끝 없음 · 계속 반복)", () => {
    expect(isInvalid("", true)).toBe(false);
    expect(isInvalid("0", true)).toBe(true);
  });

  it("글자와 숫자를 오간다", () => {
    expect(toInt(" 30 ")).toBe(30);
    expect(toInt("")).toBeNull();
    expect(toText(null)).toBe("");
    expect(toText(8)).toBe("8");
  });
});
