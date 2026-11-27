import { LoginScreen } from "@/features/auth/LoginScreen";
import { EXPIRED_PARAM, NEXT_PARAM } from "@/features/auth/returnTo";

// S-01 로그인 · 새 비밀번호. 사이드바 · 상단 바 없는 페이지 (SCR-COM-01, SCR-S01-02).
// /login?expired=1&next=… : 로그인 만료 안내와 다시 로그인한 뒤 돌아갈 주소 (#167)
type SearchParams = Promise<Record<string, string | string[] | undefined>>;

function first(value: string | string[] | undefined): string | null {
  return (Array.isArray(value) ? value[0] : value) ?? null;
}

export default async function LoginPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  return (
    <div style={{ display: "grid", placeItems: "center", minHeight: "100vh" }}>
      <LoginScreen next={first(params[NEXT_PARAM])} expired={first(params[EXPIRED_PARAM]) === "1"} />
    </div>
  );
}
