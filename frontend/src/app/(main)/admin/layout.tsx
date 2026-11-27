import type { ReactNode } from "react";
import { ForbiddenNotice } from "@/features/auth/ForbiddenNotice";
import { getStaff } from "@/features/auth/staff";

// 백오피스(B-01~B-06)는 관리자만 (FR-AUTH-002, POL-ACC-002). middleware.ts가 먼저 막고, 여기서 한 번 더 확인한다.
// 백오피스 메뉴는 Sidebar의 관리자 구역에 있다 (SCR-COM-02).
export default async function AdminLayout({ children }: { children: ReactNode }) {
  const staff = await getStaff();
  return staff?.role === "ADMIN" ? children : <ForbiddenNotice />;
}
