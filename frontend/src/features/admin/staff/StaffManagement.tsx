"use client";

import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Notice } from "@/components/ui/Notice";
import { Select } from "@/components/ui/Select";
import { TextField } from "@/components/ui/TextField";
import { ROLE_LABEL } from "@/features/auth/access";
import { createStaff, listStaff, updateStaff, type StaffItem } from "@/lib/api/admin";
import { fetchMe } from "@/lib/api/auth";
import { ApiError, LOAD_FAILED_MESSAGE, SAVE_FAILED_MESSAGE, newIdempotencyKey } from "@/lib/api/client";
import { useDialogFocus } from "@/lib/hooks/useDialogFocus";
import type { Role } from "@/types/common";
import {
  LOGIN_ID_MAX,
  NAME_MAX,
  ROLE_OPTIONS,
  STAFF_COPY,
  formatLastLogin,
  staffFormErrors,
  statusLabel,
  type StaffForm,
} from "./staffRules";
import styles from "./StaffManagement.module.css";

const EMPTY_FORM: StaffForm = { name: "", loginId: "", role: "" };

// B-01 직원 관리 (#150, FR-ADM-001, 화면 설계 SCR-B01). 관리자만 (서버도 막는다, NFR-SEC-002).
// 직원 추가 → 임시 비밀번호 모달(한 번만 보임). 역할 바꾸기 · 비활성화는 줄에서. 퇴사 직원은 지우지 않는다 (POL-ACC-004).
// [비밀번호 초기화]는 #183, 입력 중 나가기 경고는 #184가 더한다.
export function StaffManagement() {
  const [staff, setStaff] = useState<StaffItem[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [myStaffId, setMyStaffId] = useState<number | null>(null);
  const [rowError, setRowError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState<StaffForm>(EMPTY_FORM);
  const [touched, setTouched] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);
  const [loginIdError, setLoginIdError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveKey, setSaveKey] = useState(newIdempotencyKey);
  const [tempPassword, setTempPassword] = useState<string | null>(null);
  const loadSeq = useRef(0);
  const addDialog = useRef<HTMLFormElement>(null);
  const tempDialog = useRef<HTMLDivElement>(null);
  const closeAdd = useCallback(() => {
    if (!saving) setAdding(false);
  }, [saving]);
  const closeTemp = useCallback(() => setTempPassword(null), []);
  useDialogFocus(addDialog, adding, closeAdd);
  useDialogFocus(tempDialog, tempPassword !== null, closeTemp);

  // 저장 뒤 목록을 다시 부를 때 응답 순서가 바뀌어도 마지막에 부른 결과만 보인다 (#150 리뷰)
  const load = useCallback(async () => {
    const seq = ++loadSeq.current;
    try {
      const list = await listStaff();
      if (seq !== loadSeq.current) return;
      setStaff(list);
      setLoadError(null);
    } catch (e) {
      if (seq !== loadSeq.current) return;
      setLoadError(e instanceof ApiError ? e.message : LOAD_FAILED_MESSAGE);
    }
  }, []);

  useEffect(() => {
    void load();
    fetchMe()
      .then((me) => setMyStaffId(me.staffId))
      .catch(() => setMyStaffId(null));
  }, [load]);

  function openAdd() {
    setForm(EMPTY_FORM);
    setTouched(false);
    setAddError(null);
    setLoginIdError(null);
    setSaveKey(newIdempotencyKey());
    setAdding(true);
  }

  async function submitAdd(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (Object.keys(staffFormErrors(form)).length > 0 || !form.role) return;
    setSaving(true);
    setAddError(null);
    setLoginIdError(null);
    try {
      const created = await createStaff(
        { name: form.name.trim(), loginId: form.loginId.trim(), role: form.role },
        { idempotencyKey: saveKey },
      );
      setAdding(false);
      setTempPassword(created.tempPassword);
      setSaveKey(newIdempotencyKey());
      await load();
    } catch (e) {
      // 저장 실패: 입력은 그대로 둔다 (SCR-B01-03, POL-ERR-002). 같은 아이디는 칸 아래에 (정책 4-2)
      // 키는 그대로 둔다: 응답만 잃었으면 같은 키로 다시 보내 서버가 처음 응답(임시 비밀번호)을 돌려준다 (K15, #150 리뷰).
      // 입력 오류 · 중복(4xx)은 서버가 기억하지 않아 고쳐서 같은 키로 보내도 새로 처리된다
      if (e instanceof ApiError && e.code === "LOGIN_ID_TAKEN") {
        setLoginIdError(e.message);
      } else {
        setAddError(e instanceof ApiError ? e.message : SAVE_FAILED_MESSAGE);
      }
    } finally {
      setSaving(false);
    }
  }

  async function change(item: StaffItem, patch: { role?: Role; isActive?: boolean }) {
    setBusyId(item.staffId);
    setRowError(null);
    try {
      await updateStaff(item.staffId, patch, { idempotencyKey: newIdempotencyKey() });
      await load();
    } catch (e) {
      setRowError(e instanceof ApiError ? e.message : SAVE_FAILED_MESSAGE);
    } finally {
      setBusyId(null);
    }
  }

  const errors = touched ? staffFormErrors(form) : {};

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>직원 관리</h1>
          <p className={styles.guide}>{STAFF_COPY.listGuide}</p>
        </div>
        <Button onClick={openAdd}>직원 추가</Button>
      </header>

      {loadError && (
        <Notice tone="danger" actions={<Button variant="secondary" size="sm" onClick={() => void load()}>다시 시도</Button>}>
          {loadError}
        </Notice>
      )}
      {rowError && <Notice tone="danger">{rowError}</Notice>}

      {!staff && !loadError && (
        <div className={styles.card} aria-busy="true">
          {[0, 1, 2].map((i) => (
            <div key={i} className={styles.skeletonRow} />
          ))}
        </div>
      )}

      {staff && (
        <div className={styles.card}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">이름</th>
                <th scope="col">아이디</th>
                <th scope="col">역할</th>
                <th scope="col">상태</th>
                <th scope="col">마지막 로그인</th>
                <th scope="col" />
              </tr>
            </thead>
            <tbody>
              {staff.map((item) => {
                const isMe = item.staffId === myStaffId;
                const busy = busyId === item.staffId;
                return (
                  <tr key={item.staffId} className={item.isActive ? undefined : styles.inactive}>
                    <td>{item.name}</td>
                    <td>{item.loginId}</td>
                    <td>
                      {item.isActive && !isMe ? (
                        <select
                          className={styles.roleSelect}
                          aria-label={`${item.name} 역할`}
                          value={item.role}
                          disabled={busy}
                          onChange={(e) => void change(item, { role: e.target.value as Role })}
                        >
                          {ROLE_OPTIONS.map((o) => (
                            <option key={o.value} value={o.value}>
                              {o.label}
                            </option>
                          ))}
                        </select>
                      ) : (
                        ROLE_LABEL[item.role]
                      )}
                    </td>
                    <td>
                      <Badge tone={item.isActive ? "brand" : "neutral"}>{statusLabel(item.isActive)}</Badge>
                    </td>
                    <td>{formatLastLogin(item.lastLoginAt)}</td>
                    <td>
                      {item.isActive && !isMe && (
                        <Button
                          variant="danger"
                          size="sm"
                          loading={busy}
                          onClick={() => void change(item, { isActive: false })}
                        >
                          비활성화
                        </Button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {adding && (
        <div className={styles.backdrop}>
          <form
            ref={addDialog}
            className={styles.dialog}
            role="dialog"
            aria-modal="true"
            aria-labelledby="staff-add-title"
            onSubmit={submitAdd}
            noValidate
          >
            <h2 id="staff-add-title" className={styles.dialogTitle}>
              직원 추가
            </h2>
            <p className={styles.dialogText}>{STAFF_COPY.addGuide}</p>
            {addError && <Notice tone="danger">{addError}</Notice>}
            <TextField
              label="이름"
              required
              value={form.name}
              maxLength={NAME_MAX}
              onChange={(name) => setForm({ ...form, name })}
              error={errors.name}
            />
            <TextField
              label="아이디"
              required
              value={form.loginId}
              maxLength={LOGIN_ID_MAX}
              autoComplete="off"
              onChange={(loginId) => {
                setForm({ ...form, loginId });
                setLoginIdError(null);
              }}
              error={errors.loginId ?? loginIdError ?? undefined}
            />
            <Select
              label="역할"
              required
              options={ROLE_OPTIONS}
              value={form.role}
              placeholder="역할 고르기"
              onChange={(role) => setForm({ ...form, role: role as Role })}
              error={errors.role}
            />
            <div className={styles.actions}>
              <Button variant="secondary" onClick={() => setAdding(false)} disabled={saving}>
                취소
              </Button>
              <Button type="submit" loading={saving}>
                추가
              </Button>
            </div>
          </form>
        </div>
      )}

      {tempPassword && (
        <div className={styles.backdrop}>
          <div
            ref={tempDialog}
            className={styles.dialog}
            role="dialog"
            aria-modal="true"
            aria-labelledby="temp-password-title"
          >
            <h2 id="temp-password-title" className={styles.dialogTitle}>
              임시 비밀번호
            </h2>
            <p className={styles.dialogText}>{STAFF_COPY.tempPasswordGuide}</p>
            <p className={styles.tempPassword}>{tempPassword}</p>
            <div className={styles.actions}>
              <Button onClick={() => setTempPassword(null)}>확인</Button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
