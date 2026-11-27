"use client";

import { useEffect, useRef, useState } from "react";
import { fetchMe, type CurrentStaff } from "@/lib/api/auth";
import { LoginForm } from "./LoginForm";
import { NewPasswordForm } from "./NewPasswordForm";

// S-01: 로그인 카드 ↔ 새 비밀번호 카드 (#150, SCR-S01-02). 임시 비밀번호로 로그인했으면 새 비밀번호 카드.
// 새로고침해도 /api/auth/me의 mustChangePassword로 새 비밀번호 카드를 지킨다 (API 설계 3-1).
export function LoginScreen({ next = null, expired = false }: { next?: string | null; expired?: boolean }) {
  const [mustChange, setMustChange] = useState<CurrentStaff | null>(null);
  // 로그인을 누른 뒤에 도착한 처음 /me 응답은 버린다. 다른 직원의 옛 세션 응답이 로그인 결과를 덮지 않게 (#150 리뷰)
  const loginStarted = useRef(false);

  useEffect(() => {
    fetchMe()
      .then((me) => {
        if (!loginStarted.current && me.mustChangePassword) setMustChange(me);
      })
      .catch(() => undefined); // 로그인 전(401)이면 로그인 카드
  }, []);

  return mustChange ? (
    <NewPasswordForm role={mustChange.role} next={next} />
  ) : (
    <LoginForm
      next={next}
      expired={expired}
      onSubmitStart={() => {
        loginStarted.current = true;
      }}
      onMustChangePassword={setMustChange}
    />
  );
}
