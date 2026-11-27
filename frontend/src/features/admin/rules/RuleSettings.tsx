"use client";

import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Checkbox } from "@/components/ui/Checkbox";
import { ErrorState } from "@/components/ui/ErrorState";
import { Notice } from "@/components/ui/Notice";
import { Toast } from "@/components/ui/Toast";
import {
  getCoatIntervals,
  getVaccineRules,
  saveCoatIntervals,
  saveVaccineRules,
  type CoatInterval,
  type VaccineRuleGroup,
  type VaccineRuleRow,
} from "@/lib/api/admin";
import { useLeaveConfirm } from "@/lib/hooks/useLeaveConfirm";
import { useQueryWithRetry } from "@/lib/hooks/useQueryWithRetry";
import { useSave } from "@/lib/hooks/useSave";
import { isInvalid, RULE_COPY, toInt, toText } from "./ruleForm";
import styles from "./AdminForm.module.css";

// B-02 주기 규칙 (#175, FR-ADM-002 · 003). 관리자만 (admin/layout.tsx, 서버도 막는다 NFR-SEC-002).
// 접종 규칙 표와 미용 기본 간격을 따로 저장한다 (API가 다르다, API 설계 3-10). 저장하면 아직 오지 않은 예정일만 다시 계산 (POL-CYC-005).
// 입력 중 나가면 나가기 경고 (FR-COM-003, #184).

type RuleField = "ageFromDays" | "ageToDays" | "intervalDays" | "doseCount";
type RuleDraft = Record<RuleField, string>;

const RULE_COLUMNS: { field: RuleField; label: string; optional: boolean }[] = [
  { field: "ageFromDays", label: "시작 나이(일)", optional: false },
  { field: "ageToDays", label: "끝 나이(일, 비우면 끝 없음)", optional: true },
  { field: "intervalDays", label: "간격(일)", optional: false },
  { field: "doseCount", label: "횟수(비우면 계속)", optional: true },
];

// DB 설계 4-7 vaccine_rule.phase
const PHASE_LABEL: Record<VaccineRuleRow["phase"], string> = {
  PRIMARY: "기초접종",
  BOOSTER: "추가접종",
  PREVENTIVE: "예방약",
};

function ruleDraft(rule: VaccineRuleRow): RuleDraft {
  return {
    ageFromDays: toText(rule.ageFromDays),
    ageToDays: toText(rule.ageToDays),
    intervalDays: toText(rule.intervalDays),
    doseCount: toText(rule.doseCount),
  };
}

export function RuleSettings() {
  const { data, failure, loading, retry } = useQueryWithRetry(
    () => Promise.all([getVaccineRules(), getCoatIntervals()]),
    [],
  );
  const groups = data?.[0] ?? null;
  const coats = data?.[1] ?? null;

  const [rules, setRules] = useState<Record<number, RuleDraft>>({});
  const [majors, setMajors] = useState<Record<number, boolean>>({});
  const [weeks, setWeeks] = useState<Record<number, string>>({});
  const [rulesTouched, setRulesTouched] = useState(false);
  const [coatTouched, setCoatTouched] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const ruleSave = useSave();
  const coatSave = useSave();

  // 불러온 값(저장 뒤 다시 불러온 값 포함)으로 입력 칸을 채운다
  useEffect(() => {
    if (!groups || !coats) return;
    setRules(Object.fromEntries(groups.flatMap((g) => g.rules.map((r) => [r.vaccineRuleId, ruleDraft(r)]))));
    setMajors(Object.fromEntries(groups.map((g) => [g.vaccineType.vaccineTypeId, g.vaccineType.isMajor])));
    setWeeks(Object.fromEntries(coats.map((c) => [c.coatTypeId, toText(c.defaultGroomWeeks)])));
    setRulesTouched(false);
    setCoatTouched(false);
  }, [groups, coats]);

  const changedGroups = useMemo<VaccineRuleGroup[]>(() => {
    if (!groups) return [];
    return groups.flatMap((g) => {
      const major = majors[g.vaccineType.vaccineTypeId] ?? g.vaccineType.isMajor;
      const changedRules = g.rules.filter((r) => {
        const d = rules[r.vaccineRuleId];
        return d && RULE_COLUMNS.some(({ field }) => d[field] !== toText(r[field]));
      });
      if (major === g.vaccineType.isMajor && changedRules.length === 0) return [];
      return [
        {
          vaccineType: { ...g.vaccineType, isMajor: major },
          rules: changedRules.map((r) => {
            const d = rules[r.vaccineRuleId];
            return {
              ...r,
              ageFromDays: toInt(d.ageFromDays) as number,
              ageToDays: toInt(d.ageToDays),
              intervalDays: toInt(d.intervalDays) as number,
              doseCount: toInt(d.doseCount),
            };
          }),
        },
      ];
    });
  }, [groups, rules, majors]);

  const changedCoats = useMemo<CoatInterval[]>(
    () =>
      (coats ?? [])
        .filter((c) => (weeks[c.coatTypeId] ?? "") !== toText(c.defaultGroomWeeks))
        .map((c) => ({ ...c, defaultGroomWeeks: toInt(weeks[c.coatTypeId]) as number })),
    [coats, weeks],
  );

  const ruleErrors = Object.values(rules).some((d) => RULE_COLUMNS.some(({ field, optional }) => isInvalid(d[field], optional)));
  const coatErrors = Object.values(weeks).some((w) => isInvalid(w));
  const dirty = changedGroups.length > 0 || changedCoats.length > 0;
  const { leaveDialog } = useLeaveConfirm(dirty);

  async function submitRules() {
    setRulesTouched(true);
    if (ruleErrors || changedGroups.length === 0) return;
    const result = await ruleSave.save((key) => saveVaccineRules(changedGroups, { idempotencyKey: key }), changedGroups);
    if (result) {
      setToast(RULE_COPY.rulesSaved);
      retry();
    }
  }

  async function submitCoats() {
    setCoatTouched(true);
    if (coatErrors || changedCoats.length === 0) return;
    const result = await coatSave.save((key) => saveCoatIntervals(changedCoats, { idempotencyKey: key }), changedCoats);
    if (result) {
      setToast(RULE_COPY.rulesSaved);
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
      <h1 className={styles.title}>주기 규칙</h1>

      <div className={styles.section}>
        <h2 className={styles.sectionTitle}>접종 규칙</h2>
        <p className={styles.guide}>{RULE_COPY.majorGuide}</p>
        {rulesTouched && ruleErrors && <Notice tone="danger">{RULE_COPY.invalid}</Notice>}
        {ruleSave.failure && <Notice tone="danger">{ruleSave.failure.message}</Notice>}
        <div className={styles.card} aria-busy={!groups}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">구분</th>
                {RULE_COLUMNS.map((c) => (
                  <th key={c.field} scope="col">
                    {c.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(groups ?? []).map((g) => (
                <RuleGroupRows
                  key={g.vaccineType.vaccineTypeId}
                  group={g}
                  major={majors[g.vaccineType.vaccineTypeId] ?? g.vaccineType.isMajor}
                  onMajor={(value) => setMajors((m) => ({ ...m, [g.vaccineType.vaccineTypeId]: value }))}
                  drafts={rules}
                  showErrors={rulesTouched}
                  onChange={(ruleId, field, value) =>
                    setRules((all) => ({ ...all, [ruleId]: { ...all[ruleId], [field]: value } }))
                  }
                />
              ))}
            </tbody>
          </table>
        </div>
        <p className={styles.guide}>{RULE_COPY.rulesGuide}</p>
        <div className={styles.footer}>
          <Button onClick={() => void submitRules()} loading={ruleSave.saving} disabled={changedGroups.length === 0}>
            접종 규칙 저장
          </Button>
        </div>
      </div>

      <div className={styles.section}>
        <h2 className={styles.sectionTitle}>미용 기본 간격</h2>
        {coatTouched && coatErrors && <Notice tone="danger">{RULE_COPY.invalid}</Notice>}
        {coatSave.failure && <Notice tone="danger">{coatSave.failure.message}</Notice>}
        <div className={styles.card} aria-busy={!coats}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">털 유형</th>
                <th scope="col">기본 간격(주)</th>
              </tr>
            </thead>
            <tbody>
              {(coats ?? []).map((c) => {
                const value = weeks[c.coatTypeId] ?? "";
                return (
                  <tr key={c.coatTypeId}>
                    <td>{c.name}</td>
                    <td>
                      <input
                        className={styles.number}
                        type="number"
                        min={1}
                        inputMode="numeric"
                        aria-label={`${c.name} 기본 간격(주)`}
                        aria-invalid={coatTouched && isInvalid(value)}
                        value={value}
                        onChange={(e) => setWeeks((w) => ({ ...w, [c.coatTypeId]: e.target.value }))}
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <p className={styles.guide}>{RULE_COPY.rulesGuide}</p>
        <div className={styles.footer}>
          <Button onClick={() => void submitCoats()} loading={coatSave.saving} disabled={changedCoats.length === 0}>
            미용 간격 저장
          </Button>
        </div>
      </div>
    </section>
  );
}

function RuleGroupRows({
  group,
  major,
  onMajor,
  drafts,
  showErrors,
  onChange,
}: {
  group: VaccineRuleGroup;
  major: boolean;
  onMajor: (value: boolean) => void;
  drafts: Record<number, RuleDraft>;
  showErrors: boolean;
  onChange: (ruleId: number, field: RuleField, value: string) => void;
}) {
  const { vaccineType } = group;
  return (
    <>
      <tr className={styles.typeRow}>
        <td colSpan={RULE_COLUMNS.length + 1}>
          <Checkbox label={`${vaccineType.name} · 주요 접종`} checked={major} onChange={onMajor} />
        </td>
      </tr>
      {group.rules.map((rule) => {
        const draft = drafts[rule.vaccineRuleId];
        if (!draft) return null;
        return (
          <tr key={rule.vaccineRuleId}>
            <td title={rule.note ?? undefined}>{PHASE_LABEL[rule.phase]}</td>
            {RULE_COLUMNS.map(({ field, label, optional }) => (
              <td key={field}>
                <input
                  className={styles.number}
                  type="number"
                  min={1}
                  inputMode="numeric"
                  aria-label={`${vaccineType.name} ${PHASE_LABEL[rule.phase]} ${label}`}
                  aria-invalid={showErrors && isInvalid(draft[field], optional)}
                  value={draft[field]}
                  onChange={(e) => onChange(rule.vaccineRuleId, field, e.target.value)}
                />
              </td>
            ))}
          </tr>
        );
      })}
    </>
  );
}
