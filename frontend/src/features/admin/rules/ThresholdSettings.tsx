"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Notice } from "@/components/ui/Notice";
import { TextField } from "@/components/ui/TextField";
import { Toast } from "@/components/ui/Toast";
import { getThresholdSettings, saveThresholdSettings } from "@/lib/api/admin";
import { useLeaveConfirm } from "@/lib/hooks/useLeaveConfirm";
import { useQueryWithRetry } from "@/lib/hooks/useQueryWithRetry";
import { useSave } from "@/lib/hooks/useSave";
import { isInvalid, RULE_COPY, toInt, toText } from "./ruleForm";
import styles from "./AdminForm.module.css";

// B-03 기준값 · 선택지 중 기준값 부분 (#175, FR-ADM-004). 선택지 부분은 #182가 이 화면에 더한다.
// 비었거나 0 이하면 저장을 막고 입력할 값을 안내한다 (POL-ERR-006). 이미 열린 주기 · 걸린 보류는 그대로 (POL-MISS-001, POL-END-003).
export function ThresholdSettings() {
  const { data, failure, loading, retry } = useQueryWithRetry(getThresholdSettings, []);
  const [miss, setMiss] = useState("");
  const [hold, setHold] = useState("");
  const [touched, setTouched] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const { save, saving, failure: saveFailure } = useSave();

  useEffect(() => {
    if (!data) return;
    setMiss(toText(data.missThresholdDays));
    setHold(toText(data.rainbowHoldDays));
    setTouched(false);
  }, [data]);

  const dirty = data !== null && (miss !== toText(data.missThresholdDays) || hold !== toText(data.rainbowHoldDays));
  const { leaveDialog } = useLeaveConfirm(dirty);
  const missError = touched && isInvalid(miss) ? RULE_COPY.invalid : undefined;
  const holdError = touched && isInvalid(hold) ? RULE_COPY.invalid : undefined;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (isInvalid(miss) || isInvalid(hold)) return;
    const input = { missThresholdDays: toInt(miss) as number, rainbowHoldDays: toInt(hold) as number };
    const result = await save((key) => saveThresholdSettings(input, { idempotencyKey: key }), input);
    if (result) {
      setToast(RULE_COPY.thresholdSaved);
      retry();
    }
  }

  if (failure && !data) {
    return <ErrorState message={failure.message} onRetry={failure.retry ? retry : undefined} retrying={loading} />;
  }

  return (
    <section className={styles.page}>
      {leaveDialog}
      {toast && <Toast message={toast} onClose={() => setToast(null)} />}
      <h1 className={styles.title}>기준값 · 선택지</h1>

      <form className={styles.section} onSubmit={(e) => void submit(e)} noValidate>
        <h2 className={styles.sectionTitle}>기준값</h2>
        {saveFailure && <Notice tone="danger">{saveFailure.message}</Notice>}
        <div className={`${styles.card} ${styles.fields}`} aria-busy={!data}>
          <TextField
            label="누락 기준(일)"
            type="number"
            min={1}
            required
            value={miss}
            onChange={setMiss}
            error={missError}
            hint={RULE_COPY.missGuide}
          />
          <TextField
            label="무지개다리 후 연락 보류 기간(일)"
            type="number"
            min={1}
            required
            value={hold}
            onChange={setHold}
            error={holdError}
            hint={RULE_COPY.holdGuide}
          />
        </div>
        <div className={styles.footer}>
          <Button type="submit" loading={saving} disabled={!dirty}>
            기준값 저장
          </Button>
        </div>
      </form>
    </section>
  );
}
