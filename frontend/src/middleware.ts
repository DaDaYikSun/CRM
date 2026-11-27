import { NextResponse, type NextRequest } from "next/server";
import { canOpen, HOME_PATH, STAFF_NAME_HEADER, STAFF_ROLE_HEADER } from "@/features/auth/access";
import type { CurrentStaff } from "@/lib/api/auth";

// 화면 라우트 보호 (FR-AUTH-002, POL-ERR-004).
// - 로그인이 없으면 /login
// - 역할이 못 여는 주소면 권한 없음 안내 화면(/forbidden)을 같은 주소로 보여 준다
// 안내용이다. API는 서버가 따로 막는다 (NFR-SEC-002).
export const config = { matcher: ["/((?!api|_next|login|favicon.ico).*)"] };

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

async function fetchStaff(request: NextRequest): Promise<CurrentStaff | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
      headers: { cookie: request.headers.get("cookie") ?? "" },
      cache: "no-store",
    });
    return response.ok ? ((await response.json()) as { data: CurrentStaff }).data : null;
  } catch {
    return null;
  }
}

export async function middleware(request: NextRequest) {
  const staff = await fetchStaff(request);
  if (!staff) return NextResponse.redirect(new URL("/login", request.url));

  const { pathname } = request.nextUrl;
  if (pathname === "/") return NextResponse.redirect(new URL(HOME_PATH[staff.role], request.url));

  const headers = new Headers(request.headers);
  headers.set(STAFF_ROLE_HEADER, staff.role);
  headers.set(STAFF_NAME_HEADER, encodeURIComponent(staff.name));
  const init = { request: { headers } };
  return canOpen(staff.role, pathname)
    ? NextResponse.next(init)
    : NextResponse.rewrite(new URL("/forbidden", request.url), init);
}
