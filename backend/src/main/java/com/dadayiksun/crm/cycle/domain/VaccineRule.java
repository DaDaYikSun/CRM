package com.dadayiksun.crm.cycle.domain;

/**
 * 접종 규칙 한 행 (DB `vaccine_rule`, DB 설계 4-7).
 *
 * @param phase        기초접종 / 추가접종 / 예방약
 * @param ageFromDays  구간 시작 나이(일). 기록이 없으면 이 나이가 첫 예정일
 * @param ageToDays    구간 끝 나이(일, 미만). null이면 끝 없음
 * @param intervalDays 마지막 접종일 + 이 값 = 다음 예정일
 * @param doseCount    이 구간에서 맞는 횟수. null이면 계속 반복
 */
public record VaccineRule(
        VaccinePhase phase,
        int ageFromDays,
        Integer ageToDays,
        int intervalDays,
        Integer doseCount) {

    public VaccineRule {
        if (phase == null) {
            throw new IllegalArgumentException("phase는 비어 있을 수 없다");
        }
        if (ageFromDays < 0) {
            throw new IllegalArgumentException("age_from_days는 0 이상이다");
        }
        if (ageToDays != null && ageToDays <= ageFromDays) {
            throw new IllegalArgumentException("age_to_days는 age_from_days보다 커야 한다");
        }
        if (intervalDays <= 0) {
            throw new IllegalArgumentException("interval_days는 0보다 커야 한다");
        }
        if (doseCount != null && doseCount <= 0) {
            throw new IllegalArgumentException("dose_count는 0보다 커야 한다");
        }
    }

    /** 횟수가 정해진 행인지 (dose_count가 있는 행) */
    public boolean hasDoseCount() {
        return doseCount != null;
    }

    /** 이 나이(일)가 이 행의 구간 안인지. 끝은 포함하지 않는다. */
    public boolean covers(long ageDays) {
        return ageDays >= ageFromDays && (ageToDays == null || ageDays < ageToDays);
    }
}
