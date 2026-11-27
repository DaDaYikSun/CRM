import { RegisterForm } from "@/features/customer/register/RegisterForm";
import { prefillFrom } from "@/features/customer/register/prefill";

// S-08 보호자 · 아이 등록 (#145). 관리자 · 데스크만 (POL-ACC-011).
// 미용사 403은 서버가 막아야 한다 (NFR-SEC-002). 서버 역할 검사는 #143(로그인 · 역할) 방식으로 더한다.
// ?phone= · ?name= 은 S-05 [새로 등록]이 넘기는 첫 입력값 (#172).
export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<{ phone?: string | string[]; name?: string | string[] }>;
}) {
  const { phone, name } = await searchParams;
  const one = (v?: string | string[]) => (Array.isArray(v) ? v[0] : v);
  return <RegisterForm initial={prefillFrom({ phone: one(phone), name: one(name) })} />;
}
