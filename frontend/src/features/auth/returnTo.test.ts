import { describe, expect, it } from "vitest";
import { afterLoginPath, loginPath } from "./returnTo";

describe("loginPath", () => {
  it("만료면 expired와 보던 주소를 싣는다", () => {
    expect(loginPath("/guardians/3?tab=pets", true)).toBe("/login?expired=1&next=%2Fguardians%2F3%3Ftab%3Dpets");
  });
  it("처음 들어온 경우는 next만", () => {
    expect(loginPath("/targets", false)).toBe("/login?next=%2Ftargets");
  });
  it("첫 화면 · 로그인 화면은 next에 넣지 않는다", () => {
    expect(loginPath("/", false)).toBe("/login");
    expect(loginPath("/login?expired=1", true)).toBe("/login?expired=1");
    expect(loginPath(null, true)).toBe("/login?expired=1");
  });
});

describe("afterLoginPath", () => {
  it("보던 화면으로 돌아간다 (검색어 포함)", () => {
    expect(afterLoginPath("/guardians/3?tab=pets", "DESK")).toBe("/guardians/3?tab=pets");
    expect(afterLoginPath("/grooming", "GROOMER")).toBe("/grooming");
    expect(afterLoginPath("/guardians/3?tab=pets#memo", "DESK")).toBe("/guardians/3?tab=pets#memo");
    expect(afterLoginPath("/targets/../search?q=a", "DESK")).toBe("/search?q=a");
  });
  it("next가 없으면 역할별 첫 화면", () => {
    expect(afterLoginPath(null, "DESK")).toBe("/dashboard");
    expect(afterLoginPath(null, "GROOMER")).toBe("/grooming");
  });
  it("이 역할이 못 여는 화면이면 첫 화면 (다른 직원이 로그인한 경우)", () => {
    expect(afterLoginPath("/admin/staff", "DESK")).toBe("/dashboard");
    expect(afterLoginPath("/dashboard", "GROOMER")).toBe("/grooming");
  });
  it("다른 사이트 주소는 받지 않는다", () => {
    for (const bad of [
      "//evil.com/x",
      "https://evil.com",
      "/\\evil.com",
      "javascript:alert(1)",
      "dashboard",
      "/login",
      // 정규화하면 //evil.example (PR #212 리뷰)
      "/a/..//evil.example",
      "/a/..//evil.example?x=1",
      "/a/../\\evil.example",
      // 제어 문자
      "/\t/evil.com",
      "/\n/evil.com",
    ]) {
      expect(afterLoginPath(bad, "ADMIN")).toBe("/dashboard");
    }
  });
});
