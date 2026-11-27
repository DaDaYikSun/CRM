import { describe, expect, it } from "vitest";
import { canOpen, HOME_PATH, menuFor } from "./access";

describe("역할별 접근", () => {
  it("첫 화면: 관리자 · 데스크는 대시보드, 미용사는 미용 탭", () => {
    expect(HOME_PATH.ADMIN).toBe("/dashboard");
    expect(HOME_PATH.DESK).toBe("/dashboard");
    expect(HOME_PATH.GROOMER).toBe("/grooming");
  });

  it("미용사는 검색 · 미용 · 아이 상세만 연다", () => {
    for (const path of ["/search", "/grooming", "/pets/3"]) expect(canOpen("GROOMER", path)).toBe(true);
    for (const path of ["/dashboard", "/targets", "/register", "/guardians/1", "/report/2", "/admin/staff", "/unknown"]) {
      expect(canOpen("GROOMER", path)).toBe(false);
    }
  });

  it("데스크는 백오피스를 못 연다", () => {
    expect(canOpen("DESK", "/admin")).toBe(false);
    expect(canOpen("DESK", "/admin/rules")).toBe(false);
    expect(canOpen("DESK", "/targets")).toBe(true);
  });

  it("관리자는 모두 연다", () => {
    expect(canOpen("ADMIN", "/admin/staff")).toBe(true);
    expect(canOpen("ADMIN", "/guardians/1")).toBe(true);
  });

  it("비슷한 이름의 주소로 규칙을 피하지 못한다", () => {
    expect(canOpen("GROOMER", "/searchx")).toBe(false);
    expect(canOpen("DESK", "/administrator")).toBe(true); // 표에 없는 주소는 관리자 · 데스크 (서버 기본과 같음)
  });

  it("메뉴: 백오피스는 관리자만, 미용사는 검색 · 미용만", () => {
    expect(menuFor("ADMIN").map((i) => i.label)).toContain("직원 관리");
    expect(menuFor("DESK").map((i) => i.label)).not.toContain("직원 관리");
    expect(menuFor("GROOMER").map((i) => i.label)).toEqual(["검색", "미용"]);
  });
});
