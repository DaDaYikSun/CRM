"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Checkbox";
import { Notice } from "@/components/ui/Notice";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { Select } from "@/components/ui/Select";
import { TextField } from "@/components/ui/TextField";
import { Toast } from "@/components/ui/Toast";
import { ApiError, LOAD_FAILED_MESSAGE, newIdempotencyKey } from "@/lib/api/client";
import {
  addPetToGuardian,
  checkPhone,
  fetchBreeds,
  fetchCoatTypes,
  registerGuardian,
  type BreedOption,
  type CoatTypeOption,
  type PhoneCheckResult,
} from "@/lib/api/customer";
import { todayKst } from "@/lib/utils/date";
import { normalizePhone } from "@/lib/utils/phone";
import {
  EMPTY_FORM,
  REGISTERED_TOAST,
  REGISTER_COPY,
  REQUIRED_LABELS,
  VALUE_CHECK_MESSAGE,
  birthInvalid,
  canSave,
  defaultCoatTypeFor,
  duplicateWarning,
  fieldFromServer,
  missingFields,
  requiredMessage,
  shouldCheckPhone,
  toPetInput,
  toRegisterInput,
  type AgeUnit,
  type RegisterFormState,
  type RegisterMode,
  type RequiredField,
} from "./registerRules";
import styles from "./RegisterForm.module.css";
import { useLeaveConfirm } from "@/lib/hooks/useLeaveConfirm";

const PHONE_CHECK_DELAY_MS = 400;

// S-08 보호자 · 아이 등록 (#145, FR-CUS-001 · 002). 관리자 · 데스크만 쓴다 (POL-ACC-011, 서버도 막는다).
export function RegisterForm({ initial }: { initial?: Partial<RegisterFormState> } = {}) {
  const router = useRouter();
  const [form, setForm] = useState<RegisterFormState>({ ...EMPTY_FORM, ...initial });
  const [mode, setMode] = useState<RegisterMode>({ kind: "new" });
  const [touched, setTouched] = useState<Set<RequiredField>>(new Set());
  const [serverErrors, setServerErrors] = useState<Partial<Record<RequiredField, string>>>({});

  const [breeds, setBreeds] = useState<BreedOption[]>([]);
  const [coatTypes, setCoatTypes] = useState<CoatTypeOption[]>([]);
  const [optionsError, setOptionsError] = useState<string | null>(null);

  const [phoneMatches, setPhoneMatches] = useState<PhoneCheckResult | null>(null);
  /** [그래도 새로 등록]을 누른 번호. 같은 번호면 경고를 다시 띄우지 않는다 */
  const [acceptedPhone, setAcceptedPhone] = useState<string | null>(null);

  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [savedPetId, setSavedPetId] = useState<number | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  // 폼 하나에 저장 키 하나. 저장이 끝날 때까지 같은 키, 성공하면 다음 저장에 새 키 (K15, frontend/README.md)
  const idempotencyKey = useRef<string | null>(null);
  const sentBody = useRef<string | null>(null);

  useEffect(() => {
    Promise.all([fetchBreeds(), fetchCoatTypes()])
      .then(([b, c]) => {
        setBreeds(b);
        setCoatTypes(c);
      })
      .catch((error: unknown) => setOptionsError(error instanceof ApiError ? error.message : LOAD_FAILED_MESSAGE));
  }, []);

  // 전화번호를 다 넣으면 바로 같은 번호를 확인한다 (POL-REG-005)
  const phoneDigits = normalizePhone(form.phone);
  useEffect(() => {
    setPhoneMatches(null);
    if (mode.kind !== "new" || !shouldCheckPhone(phoneDigits)) return;
    let stale = false;
    const timer = setTimeout(() => {
      checkPhone(phoneDigits)
        .then((result) => {
          if (!stale) setPhoneMatches(result);
        })
        .catch((error: unknown) => {
          // 확인을 못 해도 등록은 막지 않는다. 권한 없음은 알린다 (POL-ACC-011)
          if (!stale && error instanceof ApiError && error.code === "FORBIDDEN") setSaveError(error.message);
        });
    }, PHONE_CHECK_DELAY_MS);
    return () => {
      stale = true;
      clearTimeout(timer);
    };
  }, [phoneDigits, mode.kind]);

  function update<K extends keyof RegisterFormState>(key: K, value: RegisterFormState[K], field?: RequiredField) {
    setForm((prev) => ({ ...prev, [key]: value }));
    if (field) {
      setServerErrors((prev) => ({ ...prev, [field]: undefined }));
    }
  }

  function touch(field: RequiredField) {
    setTouched((prev) => new Set(prev).add(field));
  }

  function selectBreed(breedId: string) {
    update("breedId", breedId, "breedId");
    const coat = defaultCoatTypeFor(breeds, breedId);
    if (coat !== null) update("coatTypeId", coat, "coatTypeId");
  }

  const missing = new Set(missingFields(form, mode));

  function errorOf(field: RequiredField): string | undefined {
    if (serverErrors[field]) return serverErrors[field];
    if (touched.has(field) && missing.has(field)) return requiredMessage(REQUIRED_LABELS[field]);
    if (field === "birth" && birthInvalid(form)) return VALUE_CHECK_MESSAGE;
    return undefined;
  }

  async function save() {
    if (!canSave(form, mode) || saving) return;
    // 응답 없이 끊긴 뒤 본문을 고쳐 다시 저장하면 같은 키로 처음 응답이 돌아오므로, 본문이 바뀌면 키를 새로 만든다
    const body = JSON.stringify([mode, toRegisterInput(form)]);
    if (idempotencyKey.current === null || sentBody.current !== body) {
      idempotencyKey.current = newIdempotencyKey();
      sentBody.current = body;
    }
    setSaving(true);
    setSaveError(null);
    try {
      const options = { idempotencyKey: idempotencyKey.current };
      const petId =
        mode.kind === "existing"
          ? (await addPetToGuardian(mode.guardianId, toPetInput(form), options)).petId
          : (await registerGuardian(toRegisterInput(form), options)).petId;
      idempotencyKey.current = null;
      setSavedPetId(petId);
      setToast(REGISTERED_TOAST);
      // [?] #149(S-07 아이 상세) 전이라 이동할 화면이 없다. 머지 뒤 router.push(`/pets/${petId}`)로 바꾼다 (SCR-S08-07)
    } catch (error) {
      if (error instanceof ApiError) {
        const next: Partial<Record<RequiredField, string>> = {};
        for (const f of error.fields) {
          const field = fieldFromServer(f.field);
          if (field && !next[field]) next[field] = f.message;
        }
        setServerErrors(next);
        setSaveError(error.message);
      } else {
        setSaveError(String(error));
      }
    } finally {
      setSaving(false);
    }
  }

  function startOver() {
    setForm(EMPTY_FORM);
    setMode({ kind: "new" });
    setTouched(new Set());
    setServerErrors({});
    setPhoneMatches(null);
    setAcceptedPhone(null);
    setSaveError(null);
    setSavedPetId(null);
    idempotencyKey.current = null;
  }

  const closeToast = useCallback(() => setToast(null), []);
  // 저장 전에 하나라도 입력했으면 나가기 경고 (FR-COM-003, #184). 저장이 끝나면(savedPetId) 경고하지 않는다
  const dirty = savedPetId === null && (mode.kind === "existing" || JSON.stringify(form) !== JSON.stringify(EMPTY_FORM));
  const { leaveDialog } = useLeaveConfirm(dirty);

  const showDuplicate =
    mode.kind === "new" && phoneMatches?.exists === true && acceptedPhone !== phoneDigits;

  return (
    <div className={styles.page}>
      {leaveDialog}
      <header className={styles.header}>
        <h1 className={styles.title}>보호자 · 아이 등록</h1>
        <p className={styles.intro}>{REGISTER_COPY.intro}</p>
      </header>

      {toast && <Toast message={toast} onClose={closeToast} />}

      {savedPetId !== null ? (
        <section className={styles.card}>
          <Notice tone="info" actions={<Button onClick={startOver}>새로 등록</Button>}>
            <p className={styles.noticeText}>{REGISTERED_TOAST}</p>
            <p className={styles.noticeText}>{REGISTER_COPY.afterSave}</p>
          </Notice>
        </section>
      ) : (
        <form
          className={styles.form}
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          {optionsError && <Notice tone="danger">{optionsError}</Notice>}

          <section className={styles.card} aria-labelledby="register-guardian">
            <h2 id="register-guardian" className={styles.sectionTitle}>
              보호자
            </h2>
            {mode.kind === "existing" ? (
              <Notice
                tone="info"
                actions={
                  <Button variant="secondary" size="sm" onClick={() => {
                    setAcceptedPhone(phoneDigits);
                    setMode({ kind: "new" });
                  }}>
                    그래도 새로 등록
                  </Button>
                }
              >
                <p className={styles.noticeText}>{duplicateWarning(mode.guardianName, mode.petCount)}</p>
              </Notice>
            ) : null}
            <div className={styles.grid}>
              <TextField
                label={REQUIRED_LABELS.phone}
                required
                type="tel"
                value={form.phone}
                onChange={(v) => update("phone", v, "phone")}
                onBlur={() => touch("phone")}
                error={mode.kind === "new" ? errorOf("phone") : undefined}
                autoComplete="off"
                maxLength={13}
                disabled={mode.kind === "existing"}
              />
              <TextField
                label={REQUIRED_LABELS.guardianName}
                required
                value={mode.kind === "existing" ? mode.guardianName : form.guardianName}
                onChange={(v) => update("guardianName", v, "guardianName")}
                onBlur={() => touch("guardianName")}
                error={mode.kind === "new" ? errorOf("guardianName") : undefined}
                maxLength={30}
                disabled={mode.kind === "existing"}
              />
            </div>
            {showDuplicate && phoneMatches && (
              <Notice
                tone="warning"
                actions={
                  <>
                    {phoneMatches.guardians.map((g) => (
                      <Button
                        key={g.guardianId}
                        size="sm"
                        onClick={() =>
                          setMode({ kind: "existing", guardianId: g.guardianId, guardianName: g.name, petCount: g.petCount })
                        }
                      >
                        {phoneMatches.guardians.length > 1 ? `${g.name} · 기존 보호자에 아이 추가` : "기존 보호자에 아이 추가"}
                      </Button>
                    ))}
                    <Button variant="secondary" size="sm" onClick={() => setAcceptedPhone(phoneDigits)}>
                      그래도 새로 등록
                    </Button>
                  </>
                }
              >
                {phoneMatches.guardians.map((g) => (
                  <p key={g.guardianId} className={styles.noticeText}>
                    {duplicateWarning(g.name, g.petCount)}
                  </p>
                ))}
              </Notice>
            )}
          </section>

          <section className={styles.card} aria-labelledby="register-pet">
            <h2 id="register-pet" className={styles.sectionTitle}>
              아이
            </h2>
            <div className={styles.grid}>
              <TextField
                label={REQUIRED_LABELS.petName}
                required
                value={form.petName}
                onChange={(v) => update("petName", v, "petName")}
                onBlur={() => touch("petName")}
                error={errorOf("petName")}
                maxLength={30}
              />
              <div className={styles.readonly}>
                <span className={styles.readonlyLabel}>동물 종류</span>
                <span>강아지</span>
              </div>
            </div>

            <div className={styles.birth}>
              {form.birthUnknown ? (
                <div className={styles.approx}>
                  <TextField
                    label="대략 나이"
                    required
                    type="number"
                    value={form.approxValue}
                    onChange={(v) => update("approxValue", v, "birth")}
                    onBlur={() => touch("birth")}
                    error={errorOf("birth")}
                  />
                  <SegmentedControl<AgeUnit>
                    label="대략 나이 단위"
                    options={[
                      { value: "months", label: "개월" },
                      { value: "years", label: "살" },
                    ]}
                    value={form.approxUnit}
                    onChange={(v) => update("approxUnit", v)}
                  />
                </div>
              ) : (
                <TextField
                  label={REQUIRED_LABELS.birth}
                  required
                  type="date"
                  max={todayKst()}
                  value={form.birthDate}
                  onChange={(v) => update("birthDate", v, "birth")}
                  onBlur={() => touch("birth")}
                  error={errorOf("birth")}
                />
              )}
              <Checkbox
                label="생년월일 모름"
                checked={form.birthUnknown}
                onChange={(checked) => update("birthUnknown", checked, "birth")}
              />
              <p className={styles.hint}>{REGISTER_COPY.approxAge}</p>
            </div>

            <div className={styles.grid}>
              <Select
                label={REQUIRED_LABELS.breedId}
                required
                placeholder="선택"
                options={breeds.map((b) => ({ value: String(b.breedId), label: b.name }))}
                value={form.breedId}
                onChange={selectBreed}
                onBlur={() => touch("breedId")}
                error={errorOf("breedId")}
              />
              <Select
                label={REQUIRED_LABELS.coatTypeId}
                required
                placeholder="선택"
                options={coatTypes.map((c) => ({ value: String(c.coatTypeId), label: c.name }))}
                value={form.coatTypeId}
                onChange={(v) => update("coatTypeId", v, "coatTypeId")}
                onBlur={() => touch("coatTypeId")}
                error={errorOf("coatTypeId")}
              />
            </div>
            <p className={styles.hint}>{REGISTER_COPY.options}</p>
          </section>

          {mode.kind === "new" && (
            <section className={styles.card} aria-labelledby="register-consent">
              <h2 id="register-consent" className={styles.sectionTitle}>
                동의
              </h2>
              <Checkbox
                label="개인정보 수집·이용 동의"
                checked={form.privacyAgreed}
                onChange={(checked) => update("privacyAgreed", checked)}
              />
              <Checkbox
                label="안내 문자 수신 동의"
                checked={form.smsAgreed}
                onChange={(checked) => update("smsAgreed", checked)}
              />
              <p className={styles.hint}>{REGISTER_COPY.smsConsent}</p>
            </section>
          )}

          {saveError && (
            <Notice
              tone="danger"
              actions={
                <Button size="sm" variant="danger" onClick={() => void save()} disabled={!canSave(form, mode)}>
                  다시 시도
                </Button>
              }
            >
              {saveError}
            </Notice>
          )}

          <div className={styles.actions}>
            <Button variant="secondary" onClick={() => router.back()}>
              취소
            </Button>
            <Button type="submit" loading={saving} disabled={!canSave(form, mode)}>
              저장
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}
