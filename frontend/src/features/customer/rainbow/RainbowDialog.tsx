"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Notice } from "@/components/ui/Notice";
import { LOAD_FAILED_MESSAGE, newIdempotencyKey } from "@/lib/api/client";
import { fetchSettings, markRainbow } from "@/lib/api/customer";
import { saveFailure } from "@/lib/api/failure";
import { useDialogFocus } from "@/lib/hooks/useDialogFocus";
import { rainbowConfirmMessage } from "./rainbowRules";
import styles from "./rainbow.module.css";

// S-07 무지개다리 확인 모달 (FR-CUS-007, POL-END-001). 문구의 {보류 기간}은 열 때 기준값을 읽어 채운다 (T6).
// 모달 제목 · [취소] [확인] 글자는 정책에 따로 없어 공통 확인 창 모양(CMP-U14)의 글자만 쓴다.
export function RainbowDialog({
  petId,
  petName,
  onDone,
  onClose,
}: {
  petId: number;
  petName: string;
  onDone: () => void;
  onClose: () => void;
}) {
  const [holdDays, setHoldDays] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const key = useRef<string | null>(null);
  const dialog = useRef<HTMLDivElement>(null);
  useDialogFocus(dialog, true, saving ? undefined : onClose);

  useEffect(() => {
    fetchSettings()
      .then((s) => setHoldDays(s.rainbowHoldDays))
      .catch(() => setError(LOAD_FAILED_MESSAGE));
  }, []);

  async function confirm() {
    setSaving(true);
    setError(null);
    key.current ??= newIdempotencyKey();
    try {
      await markRainbow(petId, { idempotencyKey: key.current });
      key.current = null;
      onDone();
    } catch (e) {
      setError(saveFailure(e).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className={styles.backdrop}>
      <div ref={dialog} className={styles.dialog} role="dialog" aria-modal="true" aria-labelledby="rainbow-title">
        <h2 id="rainbow-title" className={styles.title}>무지개다리로 표시</h2>
        {holdDays !== null && <p className={styles.text}>{rainbowConfirmMessage(petName, holdDays)}</p>}
        {error && <Notice tone="danger">{error}</Notice>}
        <div className={styles.buttons}>
          <Button variant="ghost" onClick={onClose} disabled={saving}>취소</Button>
          <Button variant="danger" onClick={() => void confirm()} loading={saving} disabled={holdDays === null}>확인</Button>
        </div>
      </div>
    </div>
  );
}
