import { LoginForm } from "@/features/auth/LoginForm";

// S-01 로그인. 사이드바 · 상단 바 없는 페이지 (SCR-COM-01).
export default function LoginPage() {
  return (
    <div style={{ display: "grid", placeItems: "center", minHeight: "100vh" }}>
      <LoginForm />
    </div>
  );
}
