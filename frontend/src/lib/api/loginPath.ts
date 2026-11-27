// 로그인 화면 주소 만들기 (#167). client.ts(만료 응답) · middleware.ts(화면 요청)가 함께 쓴다.
// 다시 로그인한 뒤 갈 곳을 고르는 규칙은 features/auth/returnTo.ts.

export const NEXT_PARAM = "next";
export const EXPIRED_PARAM = "expired";

export function isLoginPath(path: string): boolean {
  return path === "/login" || path.startsWith("/login?") || path.startsWith("/login/");
}

/** 로그인 화면 주소. next는 보던 화면(주소 + 검색어). 첫 화면(/)이나 로그인 화면이면 넣지 않는다 */
export function loginPath(next: string | null, expired: boolean): string {
  const params = new URLSearchParams();
  if (expired) params.set(EXPIRED_PARAM, "1");
  if (next && next !== "/" && !isLoginPath(next)) params.set(NEXT_PARAM, next);
  const query = params.toString();
  return query ? `/login?${query}` : "/login";
}
