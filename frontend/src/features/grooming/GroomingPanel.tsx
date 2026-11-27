"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { CautionTag } from "@/components/ui/CautionTag";
import { ErrorState } from "@/components/ui/ErrorState";
import { loadFailure, type Failure } from "@/lib/api/failure";
import { fetchPetDetail, fetchPetTimeline, type PetDetailResponse, type TimelineLine } from "@/lib/api/pet";
import { todayKst } from "@/lib/utils/date";
import { TAG_LABELS } from "@/features/customer/guardian/guardianRules";
import { PET_COPY, SEVERE_TAG, recordedTime, scheduleBadge } from "@/features/pet/petRules";
import { ageLabel } from "@/features/search/age";
import { VisitRecordModal } from "@/features/visit/VisitRecordModal";
import type { Role } from "@/types/common";
import { GROOMING_COPY, intervalBasisLabel } from "./groomingRules";
import styles from "./grooming.module.css";

// S-11 오른쪽 '선택한 아이' (SCR-S11-02): 이름 · 설명, 보호자 주의 태그, 다음 미용 예정 · 개인 주기와 계산 근거,
// 접종 상태(보기만), 미용 이력, [미용 기록] → S-09. 미용사는 접종을 기록하지 못한다 (POL-ACC-011, 서버도 403).
// 아이 이름 → S-07 (SCR-S11-07 [제안]). 미용 이력은 타임라인의 미용 줄 첫 쪽 (미용사도 읽을 수 있음).
export function GroomingPanel({ petId, role, onSaved }: { petId: number; role: Role; onSaved: (message: string) => void }) {
  const [detail, setDetail] = useState<PetDetailResponse | null>(null);
  const [history, setHistory] = useState<TimelineLine[] | null>(null);
  const [error, setError] = useState<Failure | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [recording, setRecording] = useState(false);
  const seq = useRef(0);
  const groomer = role === "GROOMER";

  const load = useCallback(() => {
    const mine = ++seq.current; // 늦게 온 옛 응답이 최신 값을 덮지 않게
    setError(null);
    Promise.all([fetchPetDetail(petId), fetchPetTimeline(petId, 1, "GROOMING")])
      .then(([d, t]) => {
        if (mine !== seq.current) return;
        setDetail(d);
        setHistory(t.items);
      })
      .catch((e: unknown) => {
        if (mine === seq.current) setError(loadFailure(e));
      });
  }, [petId]);

  useEffect(() => {
    load();
  }, [load, attempt]);

  if (error) {
    return (
      <aside className={styles.panel} aria-label="선택한 아이">
        <ErrorState message={error.message} onRetry={error.retry ? () => setAttempt((n) => n + 1) : undefined} />
      </aside>
    );
  }
  // 로딩 문구는 정책에 없다: 글자 없이 aria-busy만 둔다 (S-07과 같음)
  if (!detail || !history) return <aside className={styles.panel} aria-label="선택한 아이" aria-busy="true" />;

  const { pet, guardian, schedules } = detail;
  const today = todayKst();
  const rainbow = pet.status === "RAINBOW";
  const grooming = schedules.filter((s) => s.serviceType === "GROOMING");
  const vaccines = schedules.filter((s) => s.serviceType === "VACCINE");

  return (
    <aside className={styles.panel} aria-label="선택한 아이">
      <div className={styles.panelHead}>
        <h2 className={styles.panelTitle}>
          <Link href={`/pets/${pet.petId}`}>{pet.name}</Link>
        </h2>
        {rainbow && <Badge>{PET_COPY.rainbow}</Badge>}
      </div>
      <p className={styles.meta}>
        {pet.breed.name} · {pet.coatType.name} · {ageLabel(pet.birthDate, today)}
      </p>
      <div className={styles.line}>
        <span>{guardian.name}</span>
        <span className={styles.meta}>({guardian.phoneLast4})</span>
      </div>
      {guardian.tags.length > 0 && (
        <div className={styles.tags}>
          {guardian.tags.map((t) => (
            <CautionTag key={t} label={TAG_LABELS[t]} severe={t === SEVERE_TAG} />
          ))}
        </div>
      )}

      <section className={styles.section} aria-label="다음 미용 예정">
        <h3 className={styles.sectionTitle}>다음 미용 예정</h3>
        {grooming.length === 0 && <p className={styles.meta}>-</p>}
        {grooming.map((s) => {
          const badge = scheduleBadge(s.missed, s.dueDate, today);
          return (
            <div key={s.scheduleId} className={styles.line}>
              <span className={badge === "missed" ? styles.missedDate : undefined}>{s.dueDate}</span>
              {badge === "missed" && <Badge tone="danger">{PET_COPY.missed}</Badge>}
              {badge === "late" && <Badge tone="warning">{PET_COPY.late}</Badge>}
            </div>
          );
        })}
        {pet.groomInterval && (
          <p className={styles.meta}>
            개인 미용 주기 {pet.groomInterval.days}일 · {intervalBasisLabel(pet.groomInterval.basis)}
          </p>
        )}
      </section>

      <section className={styles.section} aria-label="접종 상태">
        <h3 className={styles.sectionTitle}>접종 상태 (보기만)</h3>
        {vaccines.length === 0 && <p className={styles.meta}>-</p>}
        {vaccines.map((s) => {
          const badge = scheduleBadge(s.missed, s.dueDate, today);
          return (
            <div key={s.scheduleId} className={styles.line}>
              <span>{s.vaccineType?.name ?? "접종"}</span>
              <span className={badge === "missed" ? styles.missedDate : undefined}>{s.dueDate}</span>
              {badge === "missed" && <Badge tone="danger">{PET_COPY.missed}</Badge>}
              {badge === "late" && <Badge tone="warning">{PET_COPY.late}</Badge>}
            </div>
          );
        })}
      </section>

      <section className={styles.section} aria-label="미용 이력">
        <h3 className={styles.sectionTitle}>미용 이력</h3>
        {history.length === 0 && <p className={styles.meta}>-</p>}
        {history.length > 0 && (
          <ul className={styles.history}>
            {history.map((l) => (
              <li key={l.id} className={styles.historyRow}>
                <span>
                  {l.date} · {l.summary}
                </span>
                {l.memo && <span className={styles.meta}>{l.memo}</span>}
                <span className={styles.meta}>
                  {l.staffName} · {recordedTime(l.recordedAt)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* 무지개다리 아이는 기록 버튼이 없다 (SCR-S09-03, POL-END-002) */}
      {!rainbow && (
        <section className={styles.section}>
          <p className={styles.meta}>{GROOMING_COPY.recordNote}</p>
          <Button onClick={() => setRecording(true)}>미용 기록</Button>
        </section>
      )}
      {recording && (
        <VisitRecordModal
          petId={pet.petId}
          petName={pet.name}
          initialService="GROOMING"
          groomer={groomer}
          onClose={() => setRecording(false)}
          onSaved={(message) => {
            setRecording(false);
            load(); // 다음 미용 예정일 · 이력 다시 읽기
            onSaved(message); // 목록에서 그 줄을 '미용 완료'로 (SCR-S11-03)
          }}
        />
      )}
    </aside>
  );
}
