"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Notice } from "@/components/ui/Notice";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { Select } from "@/components/ui/Select";
import { TextField } from "@/components/ui/TextField";
import { ApiError, newIdempotencyKey, SAVE_FAILED_MESSAGE } from "@/lib/api/client";
import { fetchVaccineTypes, type VaccineTypeOption } from "@/lib/api/customer";
import { recordVisit, type VisitServiceType } from "@/lib/api/visit";
import { todayKst } from "@/lib/utils/date";
import { canSave, MEMO_MAX, savedMessage, VISIT_COPY } from "./visitRules";
import styles from "./visit.module.css";

// CMP VisitRecordModal (S-09, FR-VIS-001 · 002, 화면 설계 SCR-S09). S-07 · S-11 위에 연다.
// 미용사는 접종 구분이 꺼진다 (서버도 403, T3). 데려온 사람 번호는 #178, 나가기 경고는 #184가 붙인다.
// 저장 키는 모달 하나에 하나 (K15). 실패(4xx)는 서버가 키를 기억하지 않아 입력을 고쳐 같은 키로 다시 보낸다.
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
  const [service, setService] = useState<VisitServiceType>(groomer ? "GROOMING" : initialService);
  const [types, setTypes] = useState<VaccineTypeOption[]>([]);
  const [vaccineTypeId, setVaccineTypeId] = useState("");
  const [visitDate, setVisitDate] = useState(today);
  const [memo, setMemo] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [idempotencyKey] = useState(newIdempotencyKey);

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  useEffect(() => {
    if (groomer) return;
    fetchVaccineTypes()
      .then(setTypes)
      .catch(() => setTypes([]));
  }, [groomer]);

  async function save() {
    setSaving(true);
    setError(null);
    try {
      await recordVisit(
        petId,
        {
          serviceType: service,
          vaccineTypeId: service === "VACCINE" ? Number(vaccineTypeId) : null,
          visitDate,
          memo: memo.trim() || null,
        },
        idempotencyKey,
      );
      onSaved(savedMessage(service));
    } catch (e) {
      // 자동으로 다시 보내지 않고 입력을 둔다 (POL-ERR-002). 4xx는 서버 문구(무지개다리 · 권한 · 입력 확인)
      setError(e instanceof ApiError && e.status >= 400 && e.status < 500 ? e.message : SAVE_FAILED_MESSAGE);
    } finally {
      setSaving(false);
    }
  }

  return (
    <dialog ref={dialog} className={styles.dialog} aria-labelledby="visit-title" onCancel={onClose}>
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
          <Button type="button" variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" loading={saving} disabled={!canSave(service, vaccineTypeId, visitDate, memo, today)}>
            저장
          </Button>
        </div>
      </form>
    </dialog>
  );
}
