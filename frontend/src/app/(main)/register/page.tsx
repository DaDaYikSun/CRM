import { RegisterForm } from "@/features/customer/register/RegisterForm";

// S-08 보호자 · 아이 등록 (#145). 관리자 · 데스크만 (POL-ACC-011).
// 미용사 403은 서버가 막아야 한다 (NFR-SEC-002). 서버 역할 검사는 #143(로그인 · 역할) 방식으로 더한다.
export default function RegisterPage() {
  return <RegisterForm />;
}
