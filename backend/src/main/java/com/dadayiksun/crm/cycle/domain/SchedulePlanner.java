package com.dadayiksun.crm.cycle.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 다음 예정일 정하기 (C4). 값은 모두 인자로 받는다 (규칙 · 기본 간격 · 기록은 DB에서 읽어 넘김).
 *
 * <ul>
 *   <li>접종: {@link VaccineDueDateCalculator} 그대로. 기록이 없으면 생년월일 + 첫 행 나이 (DB 설계 4-7 규칙 1).
 *       지난 날짜여도 그대로 둔다 (#144 결정: 규칙 그대로)</li>
 *   <li>미용: 기록이 있으면 마지막 미용일 + 개인 주기 (POL-CYC-002~004), 없으면 등록일 + 털 유형 기본 간격 (DB 설계 5-1, #144 결정)</li>
 * </ul>
 */
public final class SchedulePlanner {

    private SchedulePlanner() {
    }

    public static Optional<LocalDate> nextVaccineDue(PetProfile pet, List<VaccineRule> rules, List<LocalDate> doseDates) {
        return VaccineDueDateCalculator.nextDueDate(pet.birthDate(), rules, doseDates);
    }

    public static GroomPlan nextGroom(PetProfile pet, List<LocalDate> groomDates) {
        GroomInterval interval = GroomIntervalCalculator.calculate(pet.coatDefaultDays(), groomDates);
        LocalDate base = groomDates.stream().max(LocalDate::compareTo).orElse(pet.registeredOn());
        return new GroomPlan(base.plusDays(interval.days()), interval);
    }

    /** 다음 미용 예정일과 그때 쓴 개인 주기 (pet.groom_interval_days · groom_interval_basis에 남김) */
    public record GroomPlan(LocalDate dueDate, GroomInterval interval) {
    }
}
