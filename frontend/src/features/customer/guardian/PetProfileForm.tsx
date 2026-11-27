"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Notice } from "@/components/ui/Notice";
import { Select } from "@/components/ui/Select";
import { TextField } from "@/components/ui/TextField";
import { ApiError, LOAD_FAILED_MESSAGE, newIdempotencyKey } from "@/lib/api/client";
import {
  fetchBreeds,
  fetchCoatTypes,
  updatePetProfile,
  type BreedOption,
  type CoatTypeOption,
  type PetProfilePatch,
} from "@/lib/api/customer";
import { GUARDIAN_COPY, PET_SAVED_TOAST, parseWeight } from "./guardianRules";

export interface PetProfileValues {
  petId: number;
  name: string;
  birthDate: string;
  breedId: number;
  coatTypeId: number;
  weightKg: number | null;
}

// S-07 아이 프로필 수정 폼 (#151, FR-CUS-008). #149의 pets/[id] 프로필 카드의 [수정] 버튼이 이 폼을 연다 (붙이는 일은 #149 머지 뒤).
// 바뀐 칸만 보낸다. 생년월일 · 품종 · 털 유형이 바뀌면 서버가 '프로필 바뀜' 이벤트로 예정일을 다시 계산한다 (POL-REG-009).
export function PetProfileForm({
  pet,
  onSaved,
  onCancel,
}: {
  pet: PetProfileValues;
  /** 저장이 끝나면 정책 4-2 토스트 문구를 넘긴다 */
  onSaved: (toastMessage: string) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState(pet.name);
  const [birthDate, setBirthDate] = useState(pet.birthDate);
  const [birthTouched, setBirthTouched] = useState(false);
  const [breedId, setBreedId] = useState(String(pet.breedId));
  const [coatTypeId, setCoatTypeId] = useState(String(pet.coatTypeId));
  const [weight, setWeight] = useState(pet.weightKg === null ? "" : String(pet.weightKg));
  const [breeds, setBreeds] = useState<BreedOption[]>([]);
  const [coatTypes, setCoatTypes] = useState<CoatTypeOption[]>([]);
  const [showErrors, setShowErrors] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const key = useRef<string | null>(null);
  const keyBody = useRef<string | null>(null);

  useEffect(() => {
    Promise.all([fetchBreeds(), fetchCoatTypes()])
      .then(([b, c]) => {
        setBreeds(b);
        setCoatTypes(c);
      })
      .catch((e: unknown) => setError(e instanceof ApiError ? e.message : LOAD_FAILED_MESSAGE));
  }, []);

  const weightValue = parseWeight(weight);
  const errors = {
    name: name.trim() ? undefined : "아이 이름을(를) 입력해 주세요.",
    weight: weightValue === null ? "입력한 값을 확인해 주세요." : undefined,
  };

  async function save() {
    setShowErrors(true);
    if (errors.name || errors.weight) return;
    const patch: PetProfilePatch = {};
    if (name.trim() !== pet.name) patch.name = name.trim();
    // 같은 날짜라도 직접 입력했으면 보낸다: 서버가 '추정' 표시를 뗀다 (실제 생년월일 확인)
    if (birthDate && (birthDate !== pet.birthDate || birthTouched)) patch.birthDate = birthDate;
    if (Number(breedId) !== pet.breedId) patch.breedId = Number(breedId);
    if (Number(coatTypeId) !== pet.coatTypeId) patch.coatTypeId = Number(coatTypeId);
    if (typeof weightValue === "number" && weightValue !== pet.weightKg) patch.weightKg = weightValue;
    if (Object.keys(patch).length === 0) {
      onCancel();
      return;
    }
    setSaving(true);
    setError(null);
    // 응답을 잃은 뒤 내용을 고쳐 다시 보내면 옛 응답이 돌아오지 않게, 본문이 바뀌면 키를 새로 만든다
    const body = JSON.stringify(patch);
    if (key.current === null || keyBody.current !== body) {
      key.current = newIdempotencyKey();
      keyBody.current = body;
    }
    try {
      await updatePetProfile(pet.petId, patch, { idempotencyKey: key.current });
      key.current = null;
      onSaved(PET_SAVED_TOAST);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : LOAD_FAILED_MESSAGE);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={(event) => { event.preventDefault(); void save(); }}>
      <p>{GUARDIAN_COPY.petProfileNote}</p>
      <TextField label="아이 이름" required value={name} maxLength={30} onChange={setName}
        error={showErrors ? errors.name : undefined} />
      <TextField label="생년월일" type="date" value={birthDate} onChange={(v) => { setBirthDate(v); setBirthTouched(true); }} />
      <Select label="품종" required value={breedId} onChange={setBreedId}
        options={breeds.map((b) => ({ value: String(b.breedId), label: b.name }))} />
      <Select label="털 유형" required value={coatTypeId} onChange={setCoatTypeId}
        options={coatTypes.map((c) => ({ value: String(c.coatTypeId), label: c.name }))} />
      <TextField label="체중(kg)" value={weight} onChange={setWeight}
        error={showErrors ? errors.weight : undefined} />
      {error && <Notice tone="danger">{error}</Notice>}
      <Button variant="ghost" onClick={onCancel} disabled={saving}>취소</Button>
      <Button type="submit" loading={saving}>저장</Button>
    </form>
  );
}
