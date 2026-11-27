"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Notice } from "@/components/ui/Notice";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { Select } from "@/components/ui/Select";
import { TextField } from "@/components/ui/TextField";
import { ApiError, LOAD_FAILED_MESSAGE, newIdempotencyKey, SAVE_FAILED_MESSAGE } from "@/lib/api/client";
import { fetchVaccineTypes, type VaccineTypeOption } from "@/lib/api/customer";
import { recordVisit, type VisitServiceType } from "@/lib/api/visit";
import { useLeaveConfirm } from "@/lib/hooks/useLeaveConfirm";
import { todayKst } from "@/lib/utils/date";
import { canSave, isVisitDirty, MEMO_MAX, savedMessage, VISIT_COPY } from "./visitRules";
import styles from "./visit.module.css";

// CMP VisitRecordModal (S-09, FR-VIS-001 · 002, 화면 설계 SCR-S09). S-07 · S-11 위에 연다.
// 미용사는 접종 구분이 꺼진다 (서버도 403, T3). 데려온 사람 번호는 #178.
// 나가기 경고 (#184 훅, #243): 입력이 있으면 [취소] · Esc에 확인 창. 이 모달은 showModal()로 최상단 층에 뜨므로
// 확인 창을 모달 <dialog> 안에 그린다 (밖에 그리면 모달 뒤에 가려져 보이지도 눌리지도 않는다).
// 저장 키 (K15): 같은 내용으로 다시 보내면 같은 키라 두 번 저장되지 않고, 내용을 고치면 새 키로 보낸다.
export function VisitRecordModal({
  petId,
  petName,
  initialService,
  groomer,
  onSaved,
  onClose,
}: {
  petId: number;
  petName: string;
  initialService: VisitServiceType;
  groomer: boolean;
  onSaved: (message: string) => void;
  onClose: () => void;
}) {
  const today = todayKst();
  const dialog = useRef<HTMLDialogElement>(null);
  const openedService: VisitServiceType = groomer ? "GROOMING" : initialService;
  const [service, setService] = useState<VisitServiceType>(openedService);
  const [types, setTypes] = useState<VaccineTypeOption[]>([]);
  const [vaccineTypeId, setVaccineTypeId] = useState("");
  const [visitDate, setVisitDate] = useState(today);
  const [memo, setMemo] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [typesFailed, setTypesFailed] = useState(false);
  const [typesAttempt, setTypesAttempt] = useState(0);
  // 같은 내용으로 다시 보낼 때만 같은 키를 쓴다. 내용이 바뀌면 새 키 (응답을 못 받은 뒤 내용을 바꿔 보내도 옛 응답이 오지 않게)
  const lastSent = useRef<{ body: string; key: string } | null>(null);
  const { leaveDialog, confirmLeave } = useLeaveConfirm(
    isVisitDirty({ service, vaccineTypeId, visitDate, memo }, openedService, today),
  );

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  useEffect(() => {
    if (groomer) return;
    setTypesFailed(false);
    fetchVaccineTypes()
      .then(setTypes)
      .catch(() => setTypesFailed(true));
  }, [groomer, typesAttempt]);

  async function save() {
    const input = {
      serviceType: service,
      vaccineTypeId: service === "VACCINE" ? Number(vaccineTypeId) : null,
      visitDate,
      memo: memo.trim() || null,
    };
    const body = JSON.stringify(input);
    const key = lastSent.current?.body === body ? lastSent.current.key : newIdempotencyKey();
    lastSent.current = { body, key };
    setSaving(true);
    setError(null);
    try {
      await recordVisit(petId, input, key);
      onSaved(savedMessage(service));
    } catch (e) {
      // 자동으로 다시 보내지 않고 입력을 둔다 (POL-ERR-002). 4xx는 서버 문구(무지개다리 · 권한 · 입력 확인)
      setError(e instanceof ApiError && e.status >= 400 && e.status < 500 ? e.message : SAVE_FAILED_MESSAGE);
    } finally {
      setSaving(false);
    }
  }

  return (
    <dialog
      ref={dialog}
      className={styles.dialog}
      aria-labelledby="visit-title"
      onCancel={(e) => {
        e.preventDefault(); // Esc: 저장 중에는 닫지 않는다 (늦게 끝난 저장이 다음 모달을 닫지 않게)
        // 확인 창이 떠 있을 때의 Esc는 확인 창이 먼저 받아 막으므로(useDialogFocus) 여기까지 오지 않는다
        if (!saving) confirmLeave(onClose);
      }}
    >
      <form
        className={styles.form}
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <h2 id="visit-title" className={styles.title}>
          {petName} 방문 기록
        </h2>
        <SegmentedControl<VisitServiceType>
          label="구분"
          options={[
            { value: "VACCINE", label: "접종", disabled: groomer },
            { value: "GROOMING", label: "미용" },
          ]}
          value={service}
          onChange={setService}
        />
        {service === "VACCINE" && (
          <Select
            label="접종 종류"
            required
            placeholder="선택"
            options={types.map((t) => ({ value: String(t.vaccineTypeId), label: t.name }))}
            value={vaccineTypeId}
            onChange={setVaccineTypeId}
          />
        )}
        {service === "VACCINE" && typesFailed && (
          <Notice
            tone="danger"
            actions={
              <Button type="button" variant="ghost" size="sm" onClick={() => setTypesAttempt((n) => n + 1)}>
                다시 시도
              </Button>
            }
          >
            {LOAD_FAILED_MESSAGE}
          </Notice>
        )}
        <TextField
          label={service === "VACCINE" ? "방문 날짜" : "미용 날짜"}
          required
          type="date"
          value={visitDate}
          max={today}
          onChange={setVisitDate}
        />
        <label className={styles.memo}>
          <span>메모</span>
          <textarea value={memo} maxLength={MEMO_MAX} rows={3} onChange={(e) => setMemo(e.target.value)} />
          <span className={styles.hint}>{VISIT_COPY.memoGuide}</span>
        </label>
        <p className={styles.hint}>{VISIT_COPY.guide[service]}</p>
        {error && <Notice tone="danger">{error}</Notice>}
        <div className={styles.actions}>
          <Button type="button" variant="secondary" disabled={saving} onClick={() => confirmLeave(onClose)}>
            취소
          </Button>
          <Button type="submit" loading={saving} disabled={!canSave(service, vaccineTypeId, visitDate, memo, today)}>
            저장
          </Button>
        </div>
      </form>
      {leaveDialog}
    </dialog>
  );
}
