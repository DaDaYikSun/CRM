import { describe, expect, it } from "vitest";
import { leaveTarget, type LinkClick } from "./leaveTarget";

const origin = "http://crm.local";
const click = (href: string | null, extra: Partial<LinkClick> = {}): LinkClick => ({
  href,
  target: null,
  download: false,
  button: 0,
  modified: false,
  ...extra,
});

describe("leaveTarget (나가기 경고가 막을 링크, #184)", () => {
  it("이 사이트의 다른 화면으로 가는 링크는 막는다", () => {
    expect(leaveTarget(click("/targets?tab=week"), "/register", origin)).toBe("/targets?tab=week");
    expect(leaveTarget(click("/dashboard"), "/guardians/3", origin)).toBe("/dashboard");
    expect(leaveTarget(click("http://crm.local/search?q=a"), "/register", origin)).toBe("/search?q=a");
  });

  it("새 탭 · 수정 키 · 가운데 버튼 · 다운로드 · 다른 사이트는 막지 않는다", () => {
    expect(leaveTarget(click("/dashboard", { target: "_blank" }), "/register", origin)).toBeNull();
    expect(leaveTarget(click("/dashboard", { modified: true }), "/register", origin)).toBeNull();
    expect(leaveTarget(click("/dashboard", { button: 1 }), "/register", origin)).toBeNull();
    expect(leaveTarget(click("/file.csv", { download: true }), "/register", origin)).toBeNull();
    expect(leaveTarget(click("https://example.com"), "/register", origin)).toBeNull();
    expect(leaveTarget(click(null), "/register", origin)).toBeNull();
  });

  it("같은 화면 안 해시 이동은 막지 않는다", () => {
    expect(leaveTarget(click("#memo"), "/guardians/3", origin)).toBeNull();
    expect(leaveTarget(click("/guardians/3#pets"), "/guardians/3", origin)).toBeNull();
  });
});
