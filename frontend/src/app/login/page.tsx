import { LoginScreen } from "@/features/auth/LoginScreen";

// S-01 로그인 · 새 비밀번호. 사이드바 · 상단 바 없는 페이지 (SCR-COM-01, SCR-S01-02).
export default function LoginPage() {
  return (
    <div style={{ display: "grid", placeItems: "center", minHeight: "100vh" }}>
      <LoginScreen />
    </div>
  );
}
