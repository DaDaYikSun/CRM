import { PetDetail } from "@/features/pet/PetDetail";
import { getStaff } from "@/features/auth/staff";

// S-07 아이 상세 (#149). 모든 역할이 읽는다. 미용사는 화면이 줄어든다 (POL-ACC-011, 서버도 막는다).
export default async function PetPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const staff = await getStaff();
  return <PetDetail petId={Number(id)} role={staff?.role ?? "GROOMER"} />;
}
