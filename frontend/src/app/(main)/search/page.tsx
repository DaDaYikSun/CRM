import { getStaff } from "@/features/auth/staff";
import { Suspense } from "react";
import { SearchResults } from "@/features/search/SearchResults";

// S-05 통합 검색 결과 (FR-SRCH-001 · 002). 미용사도 쓴다. [새로 등록]은 관리자 · 데스크만 (SCR-S05-05).
export default async function SearchPage() {
  const staff = await getStaff();
  return (
    <Suspense>
      <SearchResults canRegister={staff?.role === "ADMIN" || staff?.role === "DESK"} />
    </Suspense>
  );
}
