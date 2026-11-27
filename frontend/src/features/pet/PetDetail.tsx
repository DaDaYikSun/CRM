"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { CautionTag } from "@/components/ui/CautionTag";
import { Notice } from "@/components/ui/Notice";
import { Toast } from "@/components/ui/Toast";
import { ApiError, LOAD_FAILED_MESSAGE } from "@/lib/api/client";
import { fetchPetDetail, type PetDetailResponse } from "@/lib/api/pet";
import { todayKst } from "@/lib/utils/date";
import { PetProfileForm } from "@/features/customer/guardian/PetProfileForm";
import { TAG_LABELS } from "@/features/customer/guardian/guardianRules";
import { ageLabel } from "@/features/search/age";
import type { Role } from "@/types/common";
import { PET_COPY, SEVERE_TAG, scheduleBadge } from "./petRules";
import { Timeline } from "./Timeline";
import styles from "./pet.module.css";

// S-07 아이 상세 (FR-VIS-003 · FR-CUS-003 · FR-CUS-005). 미용사는 프로필 · 다음 예정일 · 미용 줄만 읽고 [미용 기록]만 본다 (POL-ACC-011).
// 숨김은 편의일 뿐이고 서버가 막는다 (NFR-SEC-002). [접종 기록] · [미용 기록]은 S-09 모달(#148)이 생기면 연결한다 (지금은 눌러지지 않음).
// 무지개다리 아이의 회색 처리 · 버튼 정리는 초안 21이 맡는다.
export function PetDetail({ petId, role }: { petId: number; role: Role }) {
  const [detail, setDetail] = useState<PetDetailResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const groomer = role === "GROOMER";

  const requestSeq = useRef(0);

  const load = useCallback(() => {
    setLoadError(null);
    const seq = ++requestSeq.current; // 늦게 도착한 옛 응답이 최신 상세를 덮어쓰지 않게 가장 나중에 보낸 요청만 반영한다
    fetchPetDetail(petId)
      .then((r) => {
        if (seq === requestSeq.current) setDetail(r);
      })
      .catch((e: unknown) => {
        if (seq === requestSeq.current) setLoadError(e instanceof ApiError ? e.message : LOAD_FAILED_MESSAGE);
      });
  }, [petId]);

  useEffect(() => {
    setDetail(null);
    setEditing(false);
    load();
  }, [load]);

  const closeToast = useCallback(() => setToast(null), []);

  if (loadError) return <Notice tone="danger">{loadError}</Notice>;
  // 아이 상세의 로딩 문구는 정책에 없다: 글자 없이 스켈레톤과 aria-busy만 둔다 (문구를 지어내지 않는다)
  if (!detail) return <div className={styles.skeleton} aria-busy="true" />;
  const { pet, guardian, siblings, schedules } = detail;
  const rainbow = pet.status === "RAINBOW";
  const today = todayKst();

  return (
    <div className={styles.page}>
      {siblings.length > 0 && (
        <nav className={styles.tabs} aria-label="같은 집 아이">
          <span className={`${styles.tab} ${styles.tabCurrent}`} aria-current="page">{pet.name}</span>
          {siblings.map((s) => (
            <Link key={s.petId} href={`/pets/${s.petId}`} className={styles.tab}>
              {s.name}{s.status === "RAINBOW" && ` (${PET_COPY.rainbow})`}
            </Link>
          ))}
        </nav>
      )}

      <section className={`${styles.card} ${rainbow ? styles.rainbow : ""}`} aria-label="프로필">
        {editing ? (
          <PetProfileForm
            pet={{ petId: pet.petId, name: pet.name, birthDate: pet.birthDate, breedId: pet.breed.id,
              coatTypeId: pet.coatType.id, weightKg: pet.weightKg }}
            onSaved={(message) => { setEditing(false); setToast(message); load(); }}
            onCancel={() => setEditing(false)}
          />
        ) : (
          <>
            <div className={styles.head}>
              <h1 className={styles.title}>{pet.name}</h1>
              <Badge tone={rainbow ? "neutral" : "success"}>{rainbow ? PET_COPY.rainbow : PET_COPY.active}</Badge>
              {pet.birthDateEstimated && <Badge>{PET_COPY.birthEstimated}</Badge>}
              {!groomer && <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>수정</Button>}
            </div>
            <p className={styles.meta}>
              {pet.birthDate} ({ageLabel(pet.birthDate)}) · {pet.breed.name} · {pet.coatType.name}
            </p>
            <div className={styles.row}>
              {groomer ? guardian.name : <Link href={`/guardians/${guardian.guardianId}`}>{guardian.name}</Link>}
              <span className={styles.meta}>번호 뒤 4자리 {guardian.phoneLast4}</span>
              {guardian.tags.map((t) => (
                <CautionTag key={t} label={TAG_LABELS[t]} severe={t === SEVERE_TAG} />
              ))}
            </div>
          </>
        )}
      </section>

      <div className={styles.actions}>
        {!groomer && <Button variant="secondary" disabled>접종 기록</Button>}
        <Button variant="secondary" disabled>미용 기록</Button>
      </div>

      <section className={styles.card} aria-label="다음 예정일">
        <h2 className={styles.sectionTitle}>다음 예정일</h2>
        {pet.groomInterval && <p className={styles.meta}>개인 미용 주기 {pet.groomInterval.days}일</p>}
        {schedules.length === 0 && <p className={styles.meta}>-</p>}
        {schedules.map((s) => {
          const badge = scheduleBadge(s.missed, s.dueDate, today);
          return (
            <div key={s.scheduleId} className={styles.row}>
              <strong>{s.serviceType === "VACCINE" ? (s.vaccineType?.name ?? "접종") : "미용"}</strong>
              <span className={badge === "missed" ? styles.missedDate : undefined}>{s.dueDate}</span>
              {badge === "missed" && <Badge tone="danger">{PET_COPY.missed}</Badge>}
              {badge === "late" && <Badge tone="warning">{PET_COPY.late}</Badge>}
            </div>
          );
        })}
      </section>

      <Timeline petId={pet.petId} showFilter={!groomer} />
      {toast && <Toast message={toast} onClose={closeToast} />}
    </div>
  );
}
