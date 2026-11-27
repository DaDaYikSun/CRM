"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Checkbox";
import { ErrorState } from "@/components/ui/ErrorState";
import { Notice } from "@/components/ui/Notice";
import { Toast } from "@/components/ui/Toast";
import {
  createBreed,
  createCoatType,
  listBreeds,
  listCoatTypes,
  updateBreed,
  updateCoatType,
  type BreedInput,
  type BreedItem,
  type CoatTypeInput,
  type CoatTypeItem,
} from "@/lib/api/admin";
import { useLeaveConfirm } from "@/lib/hooks/useLeaveConfirm";
import { useQueryWithRetry } from "@/lib/hooks/useQueryWithRetry";
import { useSave } from "@/lib/hooks/useSave";
import { mergeDrafts } from "./mergeDrafts";
import { isInvalid, toInt } from "./ruleForm";
import styles from "./AdminForm.module.css";

// B-03 기준값 · 선택지 중 선택지 부분 (#182, FR-ADM-005, POL-REG-002). 등록 화면에서 고르는 품종 · 털 유형.
// 지우지 않고 '사용'을 끄면 숨긴다. 이미 그 값을 쓰는 아이는 그대로다 (DB 설계 7장).
// 털 유형의 미용 기본 간격은 추가할 때만 정한다. 바꾸는 것은 B-02 주기 규칙(예정일 다시 계산, #175).

/** 정책 4-2 '선택지 저장 (B-03)' */
const SAVED = "선택지를 저장했습니다. 등록 화면에서 바로 고를 수 있습니다.";

interface RowDraft {
  name: string;
  sortOrder: string;
  isActive: boolean;
  /** 품종만: 기본 털 유형 id, 없으면 "" */
  coat: string;
}

function breedDraft(b: BreedItem): RowDraft {
  return { name: b.name, sortOrder: String(b.sortOrder), isActive: b.isActive, coat: b.defaultCoatTypeId ? String(b.defaultCoatTypeId) : "" };
}

function coatDraft(c: CoatTypeItem): RowDraft {
  return { name: c.name, sortOrder: String(c.sortOrder), isActive: c.isActive, coat: "" };
}

function same(a: RowDraft | undefined, b: RowDraft): boolean {
  return !a || (a.name === b.name && a.sortOrder === b.sortOrder && a.isActive === b.isActive && a.coat === b.coat);
}

/** 이름이 비었거나 순서가 0 미만 정수가 아니면 저장 버튼을 끈다 (서버도 막는다) */
function rowInvalid(d: RowDraft): boolean {
  const sort = toInt(d.sortOrder);
  return d.name.trim() === "" || sort === null || Number.isNaN(sort) || sort < 0;
}

export function OptionSettings() {
  const breedQuery = useQueryWithRetry(listBreeds, []);
  const coatQuery = useQueryWithRetry(listCoatTypes, []);
  const breeds = breedQuery.data;
  const coats = coatQuery.data;

  const [breedDrafts, setBreedDrafts] = useState<Record<number, RowDraft>>({});
  const [coatDrafts, setCoatDrafts] = useState<Record<number, RowDraft>>({});
  const [newBreed, setNewBreed] = useState({ name: "", coat: "" });
  const [newCoat, setNewCoat] = useState({ name: "", weeks: "" });
  const [toast, setToast] = useState<string | null>(null);
  const breedSave = useSave();
  const coatSave = useSave();

  // 다시 불러와도 다른 줄의 저장 안 한 입력은 남긴다 (PR #256 리뷰)
  const prevBreeds = useRef<BreedItem[] | null>(null);
  const prevCoats = useRef<CoatTypeItem[] | null>(null);
  useEffect(() => {
    if (!breeds) return;
    const before = prevBreeds.current;
    setBreedDrafts((drafts) => mergeDrafts(before, breeds, drafts, (b) => b.breedId, breedDraft));
    prevBreeds.current = breeds;
  }, [breeds]);
  useEffect(() => {
    if (!coats) return;
    const before = prevCoats.current;
    setCoatDrafts((drafts) => mergeDrafts(before, coats, drafts, (c) => c.coatTypeId, coatDraft));
    prevCoats.current = coats;
  }, [coats]);

  const dirty =
    (breeds ?? []).some((b) => !same(breedDrafts[b.breedId], breedDraft(b))) ||
    (coats ?? []).some((c) => !same(coatDrafts[c.coatTypeId], coatDraft(c))) ||
    newBreed.name.trim() !== "" ||
    newCoat.name.trim() !== "" ||
    newCoat.weeks.trim() !== "";
  const { leaveDialog } = useLeaveConfirm(dirty);

  async function saveBreed(b: BreedItem) {
    const d = breedDrafts[b.breedId];
    const patch: BreedInput = {
      name: d.name.trim(),
      sortOrder: toInt(d.sortOrder) as number,
      isActive: d.isActive,
      ...(d.coat ? { defaultCoatTypeId: Number(d.coat) } : { clearDefaultCoatType: true }),
    };
    if (await breedSave.save((key) => updateBreed(b.breedId, patch, { idempotencyKey: key }), patch)) {
      setToast(SAVED);
      breedQuery.retry();
    }
  }

  async function addBreed(event: FormEvent) {
    event.preventDefault();
    const input: BreedInput = { name: newBreed.name.trim(), ...(newBreed.coat ? { defaultCoatTypeId: Number(newBreed.coat) } : {}) };
    if (!input.name) return;
    if (await breedSave.save((key) => createBreed(input, { idempotencyKey: key }), input)) {
      setNewBreed({ name: "", coat: "" });
      setToast(SAVED);
      breedQuery.retry();
    }
  }

  async function saveCoat(c: CoatTypeItem) {
    const d = coatDrafts[c.coatTypeId];
    const patch: CoatTypeInput = { name: d.name.trim(), sortOrder: toInt(d.sortOrder) as number, isActive: d.isActive };
    if (await coatSave.save((key) => updateCoatType(c.coatTypeId, patch, { idempotencyKey: key }), patch)) {
      setToast(SAVED);
      coatQuery.retry();
    }
  }

  async function addCoat(event: FormEvent) {
    event.preventDefault();
    const input: CoatTypeInput = { name: newCoat.name.trim(), defaultGroomWeeks: toInt(newCoat.weeks) as number };
    if (!input.name || isInvalid(newCoat.weeks)) return;
    if (await coatSave.save((key) => createCoatType(input, { idempotencyKey: key }), input)) {
      setNewCoat({ name: "", weeks: "" });
      setToast(SAVED);
      coatQuery.retry();
      breedQuery.retry(); // 품종의 기본 털 유형 선택지에도 보이게
    }
  }

  const loadFailure = (!breeds && breedQuery.failure) || (!coats && coatQuery.failure);
  if (loadFailure) {
    return (
      <>
        {leaveDialog}
        <ErrorState
          message={loadFailure.message}
          onRetry={
            loadFailure.retry
              ? () => {
                  if (!breeds) breedQuery.retry();
                  if (!coats) coatQuery.retry();
                }
              : undefined
          }
          retrying={breedQuery.loading || coatQuery.loading}
        />
      </>
    );
  }

  const editRow = (set: typeof setBreedDrafts, id: number, patch: Partial<RowDraft>) =>
    set((all) => ({ ...all, [id]: { ...all[id], ...patch } }));

  return (
    <div className={styles.section}>
      {leaveDialog}
      {toast && <Toast message={toast} onClose={() => setToast(null)} />}
      <h2 className={styles.sectionTitle}>선택지</h2>

      <h3 className={styles.subTitle}>품종</h3>
      {breedSave.failure && <Notice tone="danger">{breedSave.failure.message}</Notice>}
      <div className={styles.card} aria-busy={!breeds}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col">이름</th>
              <th scope="col">기본 털 유형</th>
              <th scope="col">순서</th>
              <th scope="col">사용</th>
              <th scope="col">
                <span className={styles.visuallyHidden}>저장</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {(breeds ?? []).map((b) => {
              const d = breedDrafts[b.breedId];
              if (!d) return null;
              const changed = !same(d, breedDraft(b));
              return (
                <tr key={b.breedId}>
                  <td>
                    <input className={styles.text} aria-label={`${b.name} 이름`} maxLength={50} value={d.name}
                      disabled={breedSave.saving} onChange={(e) => editRow(setBreedDrafts, b.breedId, { name: e.target.value })} />
                  </td>
                  <td>
                    <select className={styles.text} aria-label={`${b.name} 기본 털 유형`} value={d.coat}
                      disabled={breedSave.saving} onChange={(e) => editRow(setBreedDrafts, b.breedId, { coat: e.target.value })}>
                      <option value="">없음</option>
                      {(coats ?? []).filter((c) => c.isActive || String(c.coatTypeId) === d.coat).map((c) => (
                        <option key={c.coatTypeId} value={c.coatTypeId}>{c.name}</option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <input className={styles.number} type="number" min={0} aria-label={`${b.name} 순서`} value={d.sortOrder}
                      disabled={breedSave.saving} onChange={(e) => editRow(setBreedDrafts, b.breedId, { sortOrder: e.target.value })} />
                  </td>
                  <td>
                    <Checkbox label="사용" checked={d.isActive} disabled={breedSave.saving}
                      onChange={(v) => editRow(setBreedDrafts, b.breedId, { isActive: v })} />
                  </td>
                  <td>
                    <Button size="sm" variant="secondary" disabled={!changed || rowInvalid(d)} loading={breedSave.saving}
                      onClick={() => void saveBreed(b)}>저장</Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <form className={styles.addRow} onSubmit={(e) => void addBreed(e)}>
        <input className={styles.text} aria-label="새 품종 이름" placeholder="새 품종 이름" maxLength={50} value={newBreed.name}
          disabled={breedSave.saving} onChange={(e) => setNewBreed((n) => ({ ...n, name: e.target.value }))} />
        <select className={styles.text} aria-label="새 품종 기본 털 유형" value={newBreed.coat} disabled={breedSave.saving}
          onChange={(e) => setNewBreed((n) => ({ ...n, coat: e.target.value }))}>
          <option value="">기본 털 유형 없음</option>
          {(coats ?? []).filter((c) => c.isActive).map((c) => (
            <option key={c.coatTypeId} value={c.coatTypeId}>{c.name}</option>
          ))}
        </select>
        <Button type="submit" size="sm" disabled={!newBreed.name.trim()} loading={breedSave.saving}>품종 추가</Button>
      </form>

      <h3 className={styles.subTitle}>털 유형</h3>
      {coatSave.failure && <Notice tone="danger">{coatSave.failure.message}</Notice>}
      <div className={styles.card} aria-busy={!coats}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col">이름</th>
              <th scope="col">미용 기본 간격(주)</th>
              <th scope="col">순서</th>
              <th scope="col">사용</th>
              <th scope="col">
                <span className={styles.visuallyHidden}>저장</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {(coats ?? []).map((c) => {
              const d = coatDrafts[c.coatTypeId];
              if (!d) return null;
              const changed = !same(d, coatDraft(c));
              return (
                <tr key={c.coatTypeId}>
                  <td>
                    <input className={styles.text} aria-label={`${c.name} 이름`} maxLength={20} value={d.name}
                      disabled={coatSave.saving} onChange={(e) => editRow(setCoatDrafts, c.coatTypeId, { name: e.target.value })} />
                  </td>
                  <td>{c.defaultGroomWeeks}</td>
                  <td>
                    <input className={styles.number} type="number" min={0} aria-label={`${c.name} 순서`} value={d.sortOrder}
                      disabled={coatSave.saving} onChange={(e) => editRow(setCoatDrafts, c.coatTypeId, { sortOrder: e.target.value })} />
                  </td>
                  <td>
                    <Checkbox label="사용" checked={d.isActive} disabled={coatSave.saving}
                      onChange={(v) => editRow(setCoatDrafts, c.coatTypeId, { isActive: v })} />
                  </td>
                  <td>
                    <Button size="sm" variant="secondary" disabled={!changed || rowInvalid(d)} loading={coatSave.saving}
                      onClick={() => void saveCoat(c)}>저장</Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <form className={styles.addRow} onSubmit={(e) => void addCoat(e)}>
        <input className={styles.text} aria-label="새 털 유형 이름" placeholder="새 털 유형 이름" maxLength={20} value={newCoat.name}
          disabled={coatSave.saving} onChange={(e) => setNewCoat((n) => ({ ...n, name: e.target.value }))} />
        <input className={styles.number} type="number" min={1} aria-label="새 털 유형 미용 기본 간격(주)" placeholder="간격(주)"
          value={newCoat.weeks} disabled={coatSave.saving} onChange={(e) => setNewCoat((n) => ({ ...n, weeks: e.target.value }))} />
        <Button type="submit" size="sm" disabled={!newCoat.name.trim() || isInvalid(newCoat.weeks)} loading={coatSave.saving}>
          털 유형 추가
        </Button>
      </form>
    </div>
  );
}
