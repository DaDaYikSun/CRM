"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api/client";
import { login, type CurrentStaff } from "@/lib/api/auth";
import { HOME_PATH } from "./access";
import styles from "./LoginForm.module.css";

const ERROR_ID = "login-error";

// S-01 로그인 (FR-AUTH-001). 틀리면 서버가 준 정책 4-2 문구를 그대로 보여 준다. 실패 횟수로 잠그지 않는다 (POL-ACC-006).
// 임시 비밀번호로 로그인하면 onMustChangePassword로 새 비밀번호 카드로 넘긴다 (#150, LoginScreen).
// 세션 만료 안내와 보던 화면 복귀(#167)는 이 화면에 나중에 더한다.
export interface LoginFormProps {
  onMustChangePassword?: (staff: CurrentStaff) => void;
  /** [로그인]을 눌렀을 때 (LoginScreen이 먼저 부른 /me 응답을 버리게) */
  onSubmitStart?: () => void;
}

export function LoginForm({ onMustChangePassword, onSubmitStart }: LoginFormProps) {
  const router = useRouter();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    onSubmitStart?.();
    setLoading(true);
    setError(null);
    try {
      const staff = await login(loginId, password);
      if (staff.mustChangePassword && onMustChangePassword) {
        onMustChangePassword(staff);
        return;
      }
      router.replace(HOME_PATH[staff.role]);
      router.refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "저장하지 못했습니다. 다시 시도해 주세요.");
      setLoading(false);
    }
  }

  return (
    <form className={styles.card} onSubmit={submit} noValidate>
      <h1 className={styles.title}>돌아올개</h1>
      {error && (
        <p id={ERROR_ID} role="alert" className={styles.error}>
          {error}
        </p>
      )}
      <label className={styles.field}>
        아이디
        <input
          className={styles.input}
          value={loginId}
          onChange={(e) => {
            setLoginId(e.target.value);
            setError(null);
          }}
          autoComplete="username"
          aria-describedby={error ? ERROR_ID : undefined}
          autoFocus
        />
      </label>
      <label className={styles.field}>
        비밀번호
        <input
          className={styles.input}
          type="password"
          value={password}
          onChange={(e) => {
            setPassword(e.target.value);
            setError(null);
          }}
          autoComplete="current-password"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? ERROR_ID : undefined}
        />
      </label>
      <Button type="submit" size="lg" loading={loading}>
        로그인
      </Button>
      <p className={styles.hint}>
        비밀번호를 잊었으면 원장이나 데스크 실장에게 초기화를 요청해 주세요. 8시간이 지나면 자동으로 로그아웃됩니다.
      </p>
    </form>
  );
}
