package com.dadayiksun.crm.cycle.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 접종 예정일 계산 (FR-CYC-001, POL-CYC-001, DB 설계 4-7 계산 규칙).
 *
 * <p>한 접종 종류의 규칙 행과 그 종류의 접종 기록만 받아 다음 예정일을 낸다.
 * 규칙 값은 DB(`vaccine_rule`)에서 읽어 넘긴다. 이 클래스는 값을 갖고 있지 않다.
 *
 * <ol>
 *   <li>첫 예정일 = 생년월일 + 첫 행(`age_from_days`가 가장 작은 행)의 `age_from_days`.</li>
 *   <li>접종을 기록하면 행을 `age_from_days` 순으로 보고, `dose_count`를 아직 다 채우지 않은
 *       첫 행을 아이 나이와 상관없이 쓴다 (횟수 우선, 팀 결정 K24).</li>
 *   <li>다음 예정일 = 접종일 + 그 행 `interval_days`. 이번 접종으로 그 행 횟수를 다 채웠으면 다음 행의
 *       `interval_days`를 쓴다. `dose_count`가 있는 행을 모두 채웠으면(또는 그런 행이 없으면) 접종 때
 *       아이 나이가 속한 행(`dose_count` 없는 행)의 간격을 쓰고, 어느 행보다 어리면 첫 행으로 본다.</li>
 * </ol>
 */
public final class VaccineDueDateCalculator {

    private VaccineDueDateCalculator() {
    }

    /**
     * 기록이 없을 때의 첫 예정일. 규칙이 없는 종류면 비어 있다 (예정을 만들지 않음).
     */
    public static Optional<LocalDate> firstDueDate(LocalDate birthDate, List<VaccineRule> rules) {
        requireBirthDate(birthDate);
        List<VaccineRule> sorted = sorted(rules);
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(birthDate.plusDays(sorted.get(0).ageFromDays()));
    }

    /**
     * 다음 예정일. 기록이 없으면 첫 예정일이다.
     * 더 맞을 접종이 없으면(마지막 행까지 횟수를 채우고 반복 행이 없음) 비어 있다.
     *
     * @param doseDates 이 접종 종류의 접종일 (순서 무관)
     */
    public static Optional<LocalDate> nextDueDate(LocalDate birthDate, List<VaccineRule> rules,
                                                  List<LocalDate> doseDates) {
        requireBirthDate(birthDate);
        List<VaccineRule> sorted = sorted(rules);
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        if (doseDates == null || doseDates.isEmpty()) {
            return firstDueDate(birthDate, sorted);
        }
        List<LocalDate> doses = doseDates.stream().sorted().toList();
        if (doses.get(0).isBefore(birthDate)) {
            throw new IllegalArgumentException("생년월일보다 앞선 접종 기록이 있다");
        }
        LocalDate lastDose = doses.get(doses.size() - 1);

        // 규칙 2 · 3: 횟수가 정해진 행을 순서대로 채운다 (K24 횟수 우선)
        int remaining = doses.size();
        for (int i = 0; i < sorted.size(); i++) {
            VaccineRule row = sorted.get(i);
            if (!row.hasDoseCount()) {
                continue;
            }
            if (remaining < row.doseCount()) {
                return Optional.of(lastDose.plusDays(row.intervalDays()));
            }
            if (remaining == row.doseCount()) {
                if (i + 1 < sorted.size()) {
                    return Optional.of(lastDose.plusDays(sorted.get(i + 1).intervalDays()));
                }
                return Optional.empty();
            }
            remaining -= row.doseCount();
        }

        // 규칙 3: 횟수 행을 모두 채웠으면 접종 때 나이가 속한 반복 행
        long ageAtLastDose = ChronoUnit.DAYS.between(birthDate, lastDose);
        return repeatingRuleFor(sorted, ageAtLastDose)
                .map(row -> lastDose.plusDays(row.intervalDays()));
    }

    /** 반복 행(dose_count 없음) 중 그 나이가 속한 행. 어느 행보다 어리면 첫 반복 행, 행 사이 빈 나이면 앞쪽 행. */
    private static Optional<VaccineRule> repeatingRuleFor(List<VaccineRule> sorted, long ageDays) {
        List<VaccineRule> repeating = sorted.stream().filter(r -> !r.hasDoseCount()).toList();
        if (repeating.isEmpty()) {
            return Optional.empty();
        }
        VaccineRule chosen = repeating.get(0);
        for (VaccineRule row : repeating) {
            if (row.covers(ageDays)) {
                return Optional.of(row);
            }
            if (row.ageFromDays() <= ageDays) {
                chosen = row;
            }
        }
        return Optional.of(chosen);
    }

    private static List<VaccineRule> sorted(List<VaccineRule> rules) {
        if (rules == null) {
            return List.of();
        }
        return rules.stream().sorted(Comparator.comparingInt(VaccineRule::ageFromDays)).toList();
    }

    private static void requireBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new IllegalArgumentException("생년월일이 비어 있다");
        }
    }
}
