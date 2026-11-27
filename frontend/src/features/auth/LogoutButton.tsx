"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { ApiError, SAVE_FAILED_MESSAGE } from "@/lib/api/client";
import { logout } from "@/lib/api/auth";

// 위쪽 바 [로그아웃] (SCR-COM-03, POL-ACC-008, #167). 확인 창 없이 바로 로그아웃하고 로그인 화면으로 간다.
// 화면 상태를 모두 버리도록 페이지를 새로 연다. 서버에서 로그아웃하지 못했으면 이 화면에 남아 알린다.
export function LogoutButton() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onClick() {
    setLoading(true);
    setError(null);
    try {
      await logout();
      window.location.assign("/login");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : SAVE_FAILED_MESSAGE);
      setLoading(false);
    }
  }

  return (
    <>
      {error && (
        <span role="alert" style={{ color: "var(--status-danger-text)", fontSize: "var(--font-size-13)" }}>
          {error}
        </span>
      )}
      <Button variant="ghost" size="sm" loading={loading} onClick={onClick}>
        로그아웃
      </Button>
    </>
  );
}
