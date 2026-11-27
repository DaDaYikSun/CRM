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
 * 정규화한 뒤의 최종 경로도 다시 본다: /a/..//evil.com은 정규화하면 //evil.com이 된다 (CWE-601, PR #212 리뷰).
 */
export function afterLoginPath(next: string | null, role: Role): string {
  const home = HOME_PATH[role];
  // 제어 문자(탭 · 줄바꿈 등)는 URL 해석이 지워 버려 검사를 비껴갈 수 있으니 처음부터 받지 않는다
  if (!next || /[\u0000-\u001f\u007f]/.test(next)) return home;
  if (!isInternal(next) || isLoginPath(next)) return home;
  let url: URL;
  try {
    url = new URL(next, "http://crm.local");
  } catch {
    return home;
  }
  if (url.origin !== "http://crm.local" || url.pathname === "/") return home;
  const path = `${url.pathname}${url.search}${url.hash}`;
  if (!isInternal(path) || isLoginPath(path)) return home;
  return canOpen(role, url.pathname) ? path : home;
}

/** '/'로 시작하고 '//' · '/\'로 시작하지 않는 이 사이트 안의 경로인가 */
function isInternal(path: string): boolean {
  return path.startsWith("/") && !path.startsWith("//") && !path.startsWith("/\\");
}
