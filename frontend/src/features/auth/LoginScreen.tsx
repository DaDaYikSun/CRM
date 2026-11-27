"use client";

import { useEffect, useState } from "react";
import { fetchMe, type CurrentStaff } from "@/lib/api/auth";
import { LoginForm } from "./LoginForm";
import { NewPasswordForm } from "./NewPasswordForm";

// S-01: 로그인 카드 ↔ 새 비밀번호 카드 (#150, SCR-S01-02). 임시 비밀번호로 로그인했으면 새 비밀번호 카드.
// 새로고침해도 /api/auth/me의 mustChangePassword로 새 비밀번호 카드를 지킨다 (API 설계 3-1).
export function LoginScreen() {
  const [mustChange, setMustChange] = useState<CurrentStaff | null>(null);

  useEffect(() => {
    fetchMe()
      .then((me) => {
        if (me.mustChangePassword) setMustChange(me);
      })
      .catch(() => undefined); // 로그인 전(401)이면 로그인 카드
  }, []);

  return mustChange ? (
    <NewPasswordForm role={mustChange.role} />
  ) : (
    <LoginForm onMustChangePassword={setMustChange} />
  );
}
