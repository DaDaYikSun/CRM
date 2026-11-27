// 로그인 만료 뒤 복귀 (FR-AUTH-003, POL-ERR-005, SCR-COM-11 · SCR-S01-07, #167).
// 만료되면 /login?expired=1&next=<보던 주소>로 보내고, 다시 로그인하면 next로 돌아온다.
import type { Role } from "@/types/common";
import { isLoginPath } from "@/lib/api/loginPath";
import { canOpen, HOME_PATH } from "./access";

export { EXPIRED_PARAM, loginPath, NEXT_PARAM } from "@/lib/api/loginPath";

/** 정책 4-4 S-01 '세션 만료' */
export const SESSION_EXPIRED_NOTICE = "다시 로그인해 주세요. 로그인하면 보던 화면으로 돌아갑니다.";

/** 서버 세션 쿠키 이름 (Spring Session 기본, K19). 쿠키가 있는데 로그인이 없으면 만료로 본다 */
export const SESSION_COOKIE = "SESSION";

/**
 * 다시 로그인한 뒤 갈 곳. next가 이 사이트 안의 주소이고 이 역할이 열 수 있으면 next, 아니면 역할별 첫 화면.
 * 다른 사이트로 보내는 주소(//evil.com, https://…, /\evil.com)는 받지 않는다.
 */
export function afterLoginPath(next: string | null, role: Role): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\") || isLoginPath(next)) {
    return HOME_PATH[role];
  }
  let url: URL;
  try {
    url = new URL(next, "http://crm.local");
  } catch {
    return HOME_PATH[role];
  }
  if (url.origin !== "http://crm.local" || url.pathname === "/") return HOME_PATH[role];
  return canOpen(role, url.pathname) ? `${url.pathname}${url.search}${url.hash}` : HOME_PATH[role];
}
