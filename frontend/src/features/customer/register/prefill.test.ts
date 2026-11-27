import { describe, expect, it } from "vitest";
import { prefillFrom } from "./prefill";

describe("prefillFrom", () => {
  it("번호는 숫자만, 이름은 그대로", () => {
    expect(prefillFrom({ phone: "010-0000 0000" })).toEqual({ phone: "01000000000" });
    expect(prefillFrom({ name: " 김보호 " })).toEqual({ petName: "김보호" });
  });
  it("없거나 비면 아무것도 채우지 않는다", () => {
    expect(prefillFrom({})).toEqual({});
    expect(prefillFrom({ phone: "abc", name: "  " })).toEqual({});
  });
  it("길이를 자르고 제어문자를 지운다", () => {
    expect(prefillFrom({ phone: "1".repeat(50) }).phone).toHaveLength(11);
    expect(prefillFrom({ name: "가".repeat(50) }).petName).toHaveLength(30);
    expect(prefillFrom({ name: "김\n보호" }).petName).toBe("김보호");
  });
});
