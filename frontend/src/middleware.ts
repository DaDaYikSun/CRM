import { NextResponse, type NextRequest } from "next/server";
import { canOpen, HOME_PATH, STAFF_NAME_HEADER, STAFF_ROLE_HEADER } from "@/features/auth/access";
import type { CurrentStaff } from "@/lib/api/auth";
import { LOAD_FAILED_MESSAGE } from "@/lib/api/client";
import { loginPath } from "@/lib/api/loginPath";
import { SESSION_COOKIE } from "@/features/auth/returnTo";

// 화면 라우트 보호 (FR-AUTH-002, POL-ERR-004).
// - 로그인이 없으면 /login. 보던 주소를 next로 넘기고, 세션 쿠키가 있었으면(만료) 안내를 띄우게 expired=1 (#167)
// - 역할이 못 여는 주소면 권한 없음 안내 화면(/forbidden)을 같은 주소로 보여 준다
// 안내용이다. API는 서버가 따로 막는다 (NFR-SEC-002).
export const config = { matcher: ["/((?!api|_next|login|favicon.ico).*)"] };

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

// 로그인이 없으면(401) null. 서버 장애 · 네트워크 오류는 던져서 미인증과 구분한다.
async function fetchStaff(request: NextRequest): Promise<CurrentStaff | null> {
  const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
    headers: { cookie: request.headers.get("cookie") ?? "" },
    cache: "no-store",
  });
  if (response.status === 401) return null;
  if (!response.ok) throw new Error(`auth/me ${response.status}`);
  return ((await response.json()) as { data: CurrentStaff }).data;
}

export async function middleware(request: NextRequest) {
  let staff: CurrentStaff | null;
  try {
    staff = await fetchStaff(request);
  } catch {
    return new NextResponse(LOAD_FAILED_MESSAGE, { status: 503 });
  }
  const { pathname, search } = request.nextUrl;
  if (!staff) {
    const expired = request.cookies.has(SESSION_COOKIE);
    return NextResponse.redirect(new URL(loginPath(`${pathname}${search}`, expired), request.url));
  }

  if (pathname === "/") return NextResponse.redirect(new URL(HOME_PATH[staff.role], request.url));

  const headers = new Headers(request.headers);
  headers.set(STAFF_ROLE_HEADER, staff.role);
  headers.set(STAFF_NAME_HEADER, encodeURIComponent(staff.name));
  const init = { request: { headers } };
  return canOpen(staff.role, pathname)
    ? NextResponse.next(init)
    : NextResponse.rewrite(new URL("/forbidden", request.url), init);
}
