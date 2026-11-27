import { Suspense } from "react";
import { getStaff } from "@/features/auth/staff";
import { GroomingScreen } from "@/features/grooming/GroomingScreen";

// S-11 미용 (#155). 미용사의 첫 화면이고 데스크 · 관리자도 연다 (POL-ACC-007 · 011, 서버도 역할을 본다).
export default async function GroomingPage() {
  const staff = await getStaff();
  return (
    <Suspense>
      <GroomingScreen role={staff?.role ?? "GROOMER"} />
    </Suspense>
  );
}
