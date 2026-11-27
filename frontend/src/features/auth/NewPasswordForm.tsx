"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { isValidPassword } from "@/features/admin/staff/staffRules";
import { changePassword } from "@/lib/api/auth";
import { ApiError, SAVE_FAILED_MESSAGE, newIdempotencyKey } from "@/lib/api/client";
import type { Role } from "@/types/common";
import { HOME_PATH } from "./access";
import styles from "./LoginForm.module.css";

/** 정책 4-2 '임시 비밀번호 로그인' · '비밀번호 조건' */
export const TEMP_LOGIN_MESSAGE = "임시 비밀번호로 로그인했습니다. 새 비밀번호를 정해 주세요.";
export const PASSWORD_RULE_MESSAGE = "비밀번호는 8자 이상, 영문과 숫자를 섞어 주세요.";

const RULE_ID = "new-password-rule";

// S-01 '새 비밀번호 정하기' 카드 (#150, FR-ADM-007, POL-ACC-010, 화면 설계 SCR-S01-02 · 04).
// 임시 비밀번호로 로그인하면 로그인 카드 자리에 뜬다. 정하기 전에는 서버가 다른 API를 403 PASSWORD_CHANGE_REQUIRED로 막는다.
// 두 칸이 같고 조건에 맞아야 [저장하고 시작하기]를 누를 수 있다. 서버도 조건을 다시 검사한다.
export function NewPasswordForm({ role }: { role: Role }) {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  // 같은 비밀번호를 다시 보낼 때만 같은 키. 비밀번호를 고치면 새 키 (응답을 잃은 뒤 다른 값이 옛 응답을 받지 않게, #150 리뷰)
  const [saveKey, setSaveKey] = useState(newIdempotencyKey);

  const ruleBroken = password.length > 0 && !isValidPassword(password);
  const ready = isValidPassword(password) && password === confirm;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!ready) return;
    setLoading(true);
    setError(null);
    try {
      await changePassword(password, saveKey);
      router.replace(HOME_PATH[role]);
      router.refresh();
    } catch (e) {
      if (e instanceof ApiError && e.code === "SESSION_EXPIRED") {
        // 로그인이 끝났으면 로그인 카드로 돌아간다 (FR-AUTH-003, #150 리뷰)
        window.location.replace("/login");
        return;
      }
      setError(e instanceof ApiError ? e.message : SAVE_FAILED_MESSAGE);
      setLoading(false);
    }
  }

  return (
    <form className={styles.card} onSubmit={submit} noValidate>
      <h1 className={styles.title}>돌아올개</h1>
      <p role="status" className={styles.hint}>
        {TEMP_LOGIN_MESSAGE}
      </p>
      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
      <label className={styles.field}>
        새 비밀번호
        <input
          className={styles.input}
          type="password"
          value={password}
          onChange={(e) => {
            setPassword(e.target.value);
            setSaveKey(newIdempotencyKey());
            setError(null);
          }}
          autoComplete="new-password"
          aria-invalid={ruleBroken || undefined}
          aria-describedby={RULE_ID}
          autoFocus
        />
      </label>
      <label className={styles.field}>
        새 비밀번호 확인
        <input
          className={styles.input}
          type="password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          autoComplete="new-password"
          aria-invalid={(confirm.length > 0 && confirm !== password) || undefined}
        />
      </label>
      <p id={RULE_ID} className={ruleBroken ? styles.error : styles.hint}>
        {PASSWORD_RULE_MESSAGE}
      </p>
      <Button type="submit" size="lg" loading={loading} disabled={!ready}>
        저장하고 시작하기
      </Button>
    </form>
  );
}
