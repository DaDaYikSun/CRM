import { describe, expect, it } from "vitest";
import { registerHref } from "./registerHref";

describe("registerHref", () => {
  it("번호면 phone, 이름이면 name", () => {
    expect(registerHref("010-0000 0000")).toBe("/register?phone=01000000000");
    expect(registerHref(" 초코 ")).toBe("/register?name=%EC%B4%88%EC%BD%94");
    expect(registerHref("ㅊㅋ")).toContain("name=");
  });
  it("&나 다른 주소 문자는 인코딩된다", () => {
    expect(registerHref("a&phone=1")).toBe("/register?name=a%26phone%3D1");
  });
});
