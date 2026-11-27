"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Checkbox";
import { Notice } from "@/components/ui/Notice";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { TextField } from "@/components/ui/TextField";
import { Toast } from "@/components/ui/Toast";
import { ApiError, LOAD_FAILED_MESSAGE, newIdempotencyKey } from "@/lib/api/client";
import {
  fetchCurrentRole,
  fetchGuardianDetail,
  revertRainbow,
  updateGuardian,
  type GuardianDetail as Detail,
  type GuardianDetailPet,
} from "@/lib/api/customer";
import { normalizePhone } from "@/lib/utils/phone";
import {
  CONTACT_TIME_NOTE_MAX,
  GUARDIAN_COPY,
  GUARDIAN_SAVED_TOAST,
  REVERTED_TOAST,
  TAG_LABELS,
  TAG_ORDER,
  buildGuardianPatch,
  formatPhone,
  guardianFormErrors,
  holdLabel,
  revertConfirmMessage,
  toggleTag,
  type GuardianFormState,
} from "./guardianRules";
import styles from "./GuardianDetail.module.css";

function toForm(d: Detail): GuardianFormState {
  const g = d.guardian;
  return {
    name: g.name,
    phone: g.phone,
    subPhone: g.subPhone ?? "",
    preferredContact: g.preferredContact ?? "",
    tags: g.tags,
    contactTimeNote: g.contactTimeNote ?? "",
  };
}

const PREFERRED_LABEL = { PHONE: "전화", SMS: "문자" } as const;

// S-06 보호자 상세 · 수정 · 무지개다리 되돌리기 (#151, FR-CUS-008). 관리자 · 데스크만 (POL-ACC-011, 서버도 막는다).
// 연락 기록 표는 #158이 이 페이지에 추가한다.
export function GuardianDetail({ guardianId }: { guardianId: number }) {
  const [detail, setDetail] = useState<Detail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<GuardianFormState | null>(null);
  const [showErrors, setShowErrors] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [revertTarget, setRevertTarget] = useState<GuardianDetailPet | null>(null);
  const [revertError, setRevertError] = useState<string | null>(null);
  // 폼 하나에 저장 키 하나 (K15). 성공하면 지운다
  const saveKey = useRef<string | null>(null);
  const saveBody = useRef<string | null>(null);
  const revertKey = useRef<string | null>(null);

  const load = useCallback(() => {
    fetchGuardianDetail(guardianId)
      .then(setDetail)
      .catch((e: unknown) => setLoadError(e instanceof ApiError ? e.message : LOAD_FAILED_MESSAGE));
  }, [guardianId]);

  useEffect(load, [load]);

  // 버튼 숨김은 편의일 뿐이고 서버가 관리자만 허용한다. 역할을 못 받으면 버튼을 보이지 않는다
  useEffect(() => {
    fetchCurrentRole()
      .then((me) => setIsAdmin(me.role === "ADMIN"))
      .catch(() => setIsAdmin(false));
  }, []);

  const closeToast = useCallback(() => setToast(null), []);

  if (loadError) return <Notice tone="danger">{loadError}</Notice>;
  if (!detail) return null;
  const g = detail.guardian;

  function startEdit() {
    setForm(toForm(detail!));
    setShowErrors(false);
    setSaveError(null);
    setEditing(true);
  }

  async function save() {
    if (!form || !detail) return;
    setShowErrors(true);
    if (Object.keys(guardianFormErrors(form)).length > 0) return;
    setSaving(true);
    setSaveError(null);
    let base = detail;
    if (saveKey.current !== null) {
      // 앞선 저장의 결과를 모른다(응답 유실): 서버가 이미 저장했을 수 있으니 서버 상태를 기준으로 바뀐 칸을 다시 가린다
      try {
        base = await fetchGuardianDetail(guardianId);
        setDetail(base);
      } catch {
        setSaveError(LOAD_FAILED_MESSAGE);
        setSaving(false);
        return;
      }
    }
    const patch = buildGuardianPatch(toForm(base), form);
    if (!patch) {
      saveKey.current = null;
      setSaving(false);
      setEditing(false);
      return;
    }
    // 응답을 잃은 뒤 내용을 고쳐 다시 보내면 옛 응답이 돌아오지 않게, 본문이 바뀌면 키를 새로 만든다
    const body = JSON.stringify(patch);
    if (saveKey.current === null || saveBody.current !== body) {
      saveKey.current = newIdempotencyKey();
      saveBody.current = body;
    }
    try {
      setDetail(await updateGuardian(guardianId, patch, { idempotencyKey: saveKey.current }));
      saveKey.current = null;
      setEditing(false);
      setToast(GUARDIAN_SAVED_TOAST);
    } catch (e) {
      setSaveError(e instanceof ApiError ? e.message : LOAD_FAILED_MESSAGE);
    } finally {
      setSaving(false);
    }
  }

  async function confirmRevert() {
    if (!revertTarget) return;
    setRevertError(null);
    revertKey.current ??= newIdempotencyKey();
    try {
      await revertRainbow(revertTarget.petId, { idempotencyKey: revertKey.current });
      revertKey.current = null;
      setRevertTarget(null);
      setToast(REVERTED_TOAST);
      load();
    } catch (e) {
      setRevertError(e instanceof ApiError ? e.message : LOAD_FAILED_MESSAGE);
    }
  }

  const errors = form && showErrors ? guardianFormErrors(form) : {};

  return (
    <div className={styles.page}>
      <header>
        <h1 className={styles.title}>{g.name}</h1>
        <p className={styles.intro}>{GUARDIAN_COPY.subtitle}</p>
      </header>

      {g.tags.includes("NO_CONTACT") && <Notice tone="warning">{GUARDIAN_COPY.noContactNote}</Notice>}

      <section className={styles.card} aria-label="연락처">
        <div className={styles.cardHeader}>
          <h2 className={styles.sectionTitle}>연락처 · 주의 태그</h2>
          {!editing && <Button variant="secondary" size="sm" onClick={startEdit}>수정</Button>}
        </div>

        {editing && form ? (
          <>
            <div className={styles.grid}>
              <TextField label="보호자 이름" required value={form.name} maxLength={30} error={errors.name}
                onChange={(name) => setForm({ ...form, name })} />
              <TextField label="전화번호" required type="tel" value={form.phone} error={errors.phone}
                hint={GUARDIAN_COPY.phoneChangeNote} onChange={(phone) => setForm({ ...form, phone })} />
              <TextField label="보조 연락처" type="tel" value={form.subPhone} error={errors.subPhone}
                onChange={(subPhone) => setForm({ ...form, subPhone })} />
              <SegmentedControl label="선호 연락 방법"
                options={[{ value: "", label: "선택 안 함" }, { value: "PHONE", label: "전화" }, { value: "SMS", label: "문자" }]}
                value={form.preferredContact}
                onChange={(preferredContact) => setForm({ ...form, preferredContact })} />
            </div>
            <div className={styles.tags}>
              {TAG_ORDER.map((tag) => (
                <Checkbox key={tag} label={TAG_LABELS[tag]} checked={form.tags.includes(tag)}
                  onChange={() => setForm({ ...form, tags: toggleTag(form.tags, tag) })} />
              ))}
            </div>
            {form.tags.includes("NO_CONTACT") && <p className={styles.hint}>{GUARDIAN_COPY.noContactNote}</p>}
            {form.tags.includes("CONTACT_TIME") && (
              <TextField label="연락 선호 시간" value={form.contactTimeNote} maxLength={CONTACT_TIME_NOTE_MAX}
                onChange={(contactTimeNote) => setForm({ ...form, contactTimeNote })} />
            )}
            {saveError && <Notice tone="danger">{saveError}</Notice>}
            <div className={styles.actions}>
              <Button variant="ghost" onClick={() => setEditing(false)} disabled={saving}>취소</Button>
              <Button onClick={save} loading={saving}>저장</Button>
            </div>
          </>
        ) : (
          <>
            <dl className={styles.rows}>
              <dt>전화번호</dt>
              <dd>
                {formatPhone(g.phone)} · 마지막 확인 {g.phoneCheckedAt}
                {g.phoneStatus === "WRONG" && <> <Badge tone="danger">번호 오류</Badge></>}
              </dd>
              <dt>보조 연락처</dt>
              <dd>{g.subPhone ? formatPhone(normalizePhone(g.subPhone)) : "-"}</dd>
              <dt>선호 연락 방법</dt>
              <dd>{g.preferredContact ? PREFERRED_LABEL[g.preferredContact] : "-"}</dd>
              <dt>동의</dt>
              <dd>개인정보 {g.privacyAgreed ? "동의" : "미동의"} · 안내 문자 {g.smsAgreed ? "동의" : "미동의"}</dd>
              <dt>등록</dt>
              <dd>{g.createdAt.slice(0, 10)} · {g.createdByName ?? "-"}</dd>
              <dt>주의 태그</dt>
              <dd>
                <span className={styles.tags}>
                  {g.tags.length === 0 && "-"}
                  {g.tags.map((t) => (
                    <Badge key={t} tone={t === "NO_CONTACT" ? "danger" : "warning"}>{TAG_LABELS[t]}</Badge>
                  ))}
                </span>
                {g.contactTimeNote && <> {g.contactTimeNote}</>}
              </dd>
            </dl>
          </>
        )}
      </section>

      <section className={styles.card} aria-label="같은 집 아이">
        <h2 className={styles.sectionTitle}>같은 집 아이</h2>
        {detail.pets.map((p) => (
          <PetCard key={p.petId} pet={p} isAdmin={isAdmin} onRevert={() => { setRevertError(null); setRevertTarget(p); }} />
        ))}
        {revertTarget && (
          <Notice tone="warning" actions={
            <>
              <Button variant="ghost" size="sm" onClick={() => setRevertTarget(null)}>취소</Button>
              <Button size="sm" onClick={confirmRevert}>되돌리기</Button>
            </>
          }>
            {revertConfirmMessage(revertTarget.name)}
            {revertError && <div role="alert">{revertError}</div>}
          </Notice>
        )}
      </section>

      {toast && <Toast message={toast} onClose={closeToast} />}
    </div>
  );
}

function PetCard({ pet, isAdmin, onRevert }: { pet: GuardianDetailPet; isAdmin: boolean; onRevert: () => void }) {
  const rainbow = pet.status === "RAINBOW";
  const next = pet.nextSchedule;
  return (
    <div className={`${styles.petCard} ${rainbow ? styles.petRainbow : ""}`}>
      <div className={styles.petHead}>
        {pet.name}
        {rainbow ? <Badge>무지개다리</Badge> : <Badge tone="success">재방문 관리 중</Badge>}
        {pet.contactHoldUntil && <Badge tone="warning">연락 보류</Badge>}
        {pet.contactHoldUntil && <span className={styles.petMeta}>{holdLabel(pet.contactHoldUntil)}</span>}
      </div>
      <div className={styles.petMeta}>
        {pet.breedName} · {pet.coatTypeName} · {pet.birthDate}{pet.birthDateEstimated && " (추정)"}
      </div>
      {!rainbow && (
        <div className={styles.petMeta}>
          다음 예정:{" "}
          {next
            ? `${next.serviceType === "VACCINE" ? (next.vaccineTypeName ?? "접종") : "미용"} ${next.dueDate}${next.missed ? " · 누락" : ""}`
            : "-"}
        </div>
      )}
      {rainbow &&
        (isAdmin ? (
          <div><Button variant="secondary" size="sm" onClick={onRevert}>무지개다리 표시 되돌리기</Button></div>
        ) : (
          <p className={styles.hint}>{GUARDIAN_COPY.rainbowAdminOnly}</p>
        ))}
      <div><Link href={`/pets/${pet.petId}`}>아이 상세 →</Link></div>
    </div>
  );
}
