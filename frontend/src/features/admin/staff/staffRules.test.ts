import { describe, expect, it } from "vitest";
import { formatLastLogin, isValidPassword, staffFormErrors, statusLabel } from "./staffRules";

describe("B-01 직원 관리 규칙", () => {
  it("필수 칸이 비면 정책 4-2 문구", () => {
    expect(staffFormErrors({ name: "", loginId: " ", role: "" })).toEqual({
      name: "이름을(를) 입력해 주세요.",
      loginId: "아이디을(를) 입력해 주세요.",
      role: "역할을(를) 입력해 주세요.",
    });
    expect(staffFormErrors({ name: "새 데스크", loginId: "desk02", role: "DESK" })).toEqual({});
  });

  it("상태 표시", () => {
    expect(statusLabel(true)).toBe("사용 중");
    expect(statusLabel(false)).toBe("비활성");
  });

  it("마지막 로그인은 한국 시간", () => {
    expect(formatLastLogin("2026-11-27T00:01:00Z")).toBe("2026-11-27 09:01");
    expect(formatLastLogin("2026-11-27T09:01:00+09:00")).toBe("2026-11-27 09:01");
    expect(formatLastLogin(null)).toBe("-");
  });

  it("비밀번호 조건 (POL-ACC-010)", () => {
    expect(isValidPassword("abcd1234")).toBe(true);
    expect(isValidPassword("abc123")).toBe(false);
    expect(isValidPassword("abcdefgh")).toBe(false);
    expect(isValidPassword("12345678")).toBe(false);
  });
});
