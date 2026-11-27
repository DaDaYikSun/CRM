import { headers } from "next/headers";
import type { Role } from "@/types/common";
import { STAFF_NAME_HEADER, STAFF_ROLE_HEADER } from "./access";

// 서버 컴포넌트에서 로그인한 직원을 읽는다. middleware.ts가 /api/auth/me 결과를 요청 헤더에 실어 준다.
// 미들웨어가 매 요청 이 헤더를 덮어써서 브라우저가 보낸 값은 쓰이지 않는다.
export async function getStaff(): Promise<{ role: Role; name: string } | null> {
  const h = await headers();
  const role = h.get(STAFF_ROLE_HEADER) as Role | null;
  if (!role) return null;
  return { role, name: decodeURIComponent(h.get(STAFF_NAME_HEADER) ?? "") };
}
