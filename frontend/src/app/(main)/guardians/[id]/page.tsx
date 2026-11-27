import { GuardianDetail } from "@/features/customer/guardian/GuardianDetail";

// S-06 보호자 상세 (#151). 관리자 · 데스크만 (POL-ACC-011). 미용사 403은 서버가 막는다 (#143 역할 검사).
export default async function GuardianPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <GuardianDetail guardianId={Number(id)} />;
}
