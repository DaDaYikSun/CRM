// 역할별 화면 접근 · 첫 화면 · 메뉴 (화면 설계 7장 · SCR-COM-02, POL-ACC-007 · 011, T2).
// 화면에서 막는 것은 안내용이다. 서버가 API를 따로 막는다 (NFR-SEC-002, backend RoleAccessRules).
import type { SidebarItem } from "@/components/layout/Sidebar";
import type { Role } from "@/types/common";

/** middleware.ts가 로그인한 직원을 서버 컴포넌트에 넘기는 요청 헤더 (staff.ts) */
export const STAFF_ROLE_HEADER = "x-crm-staff-role";
export const STAFF_NAME_HEADER = "x-crm-staff-name";

/** 정책 4-2 '권한 없음 (모든 역할 공통)' */
export const FORBIDDEN_MESSAGE = "이 작업을 할 권한이 없습니다. 필요하면 관리자에게 문의해 주세요.";

export const ROLE_LABEL: Record<Role, string> = { ADMIN: "관리자", DESK: "데스크", GROOMER: "미용사" };

/** 로그인 뒤 첫 화면: 관리자 · 데스크 S-02 대시보드, 미용사 S-11 미용 (FR-AUTH-001, POL-ACC-007, T2) */
export const HOME_PATH: Record<Role, string> = { ADMIN: "/dashboard", DESK: "/dashboard", GROOMER: "/grooming" };

const ALL: Role[] = ["ADMIN", "DESK", "GROOMER"];
const STAFF_ONLY: Role[] = ["ADMIN", "DESK"];

/** 주소 앞부분 → 열 수 있는 역할. 표에 없는 주소는 관리자 · 데스크만 (서버 기본과 같음) */
const ROUTES: [prefix: string, roles: Role[]][] = [
  ["/forbidden", ALL],
  ["/search", ALL],
  ["/pets", ALL],
  ["/grooming", ALL],
  ["/admin", ["ADMIN"]],
  ["/dashboard", STAFF_ONLY],
  ["/targets", STAFF_ONLY],
  ["/register", STAFF_ONLY],
  ["/guardians", STAFF_ONLY],
  ["/report", STAFF_ONLY],
];

export function canOpen(role: Role, pathname: string): boolean {
  const found = ROUTES.find(([prefix]) => pathname === prefix || pathname.startsWith(`${prefix}/`));
  return (found ? found[1] : STAFF_ONLY).includes(role);
}

const MENU: (SidebarItem & { roles: Role[] })[] = [
  { href: "/dashboard", label: "대시보드", roles: STAFF_ONLY },
  { href: "/targets", label: "연락 대상", roles: STAFF_ONLY },
  { href: "/search", label: "검색", roles: ALL },
  { href: "/register", label: "새로 등록", roles: STAFF_ONLY },
  { href: "/grooming", label: "미용", roles: ALL },
  // 관리자 구역 B-01~B-06 (팀 결정 T8)
  { href: "/admin/staff", label: "직원 관리", roles: ["ADMIN"] },
  { href: "/admin/rules", label: "주기 규칙", roles: ["ADMIN"] },
  { href: "/admin/settings", label: "기준값 · 선택지", roles: ["ADMIN"] },
  { href: "/admin/message-templates", label: "문자 문구", roles: ["ADMIN"] },
  { href: "/admin/report-template", label: "리포트 양식", roles: ["ADMIN"] },
  { href: "/admin/history", label: "변경 이력", roles: ["ADMIN"] },
];

export function menuFor(role: Role): SidebarItem[] {
  return MENU.filter((item) => item.roles.includes(role)).map(({ href, label }) => ({ href, label }));
}
