package com.dadayiksun.crm.cycle.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 개인 미용 주기 계산 (FR-CYC-002, POL-CYC-002 · 003 · 004, DB 설계 5-1).
 *
 * <ul>
 *   <li>기록 0~1회: 털 유형 기본 간격 (POL-CYC-002). 기본 간격은 `coat_type.default_groom_weeks`에서 읽어 넘긴다.</li>
 *   <li>기록 2회 이상: 최근 방문 간격 최대 4개의 중앙값. 기본 간격의 2배가 넘는 간격은 다른 곳에서 미용한 것으로 보고
 *       뺀다 (POL-CYC-003). 다 빠지면 기본 간격.</li>
 *   <li>다음 미용 예정일 = 마지막 미용일 + 주기 (POL-CYC-004).</li>
 * </ul>
 * '4개' · '2배'는 정책의 고정값이다 (설정값 아님).
 */
public final class GroomIntervalCalculator {

    /** 중앙값에 쓰는 최근 간격 수 (POL-CYC-003 고정값) */
    static final int MAX_RECENT_INTERVALS = 4;
    /** 이 배수를 넘는 간격은 뺀다 (POL-CYC-003 고정값) */
    static final int OUTLIER_MULTIPLIER = 2;

    private GroomIntervalCalculator() {
    }

    /**
     * @param defaultIntervalDays 그 아이 털 유형의 기본 간격(일)
     * @param groomDates          그 아이의 미용 방문일 (순서 무관, 같은 날은 한 번으로 본다)
     */
    public static GroomInterval calculate(int defaultIntervalDays, List<LocalDate> groomDates) {
        if (defaultIntervalDays <= 0) {
            throw new IllegalArgumentException("털 유형 기본 간격은 0보다 커야 한다");
        }
        List<LocalDate> dates = groomDates == null ? List.of()
                : groomDates.stream().distinct().sorted().toList();
        if (dates.size() < 2) {
            return new GroomInterval(defaultIntervalDays, GroomIntervalBasis.DEFAULT);
        }

        // 최근 방문 최대 5건 → 간격 최대 4개
        List<LocalDate> recent = dates.subList(Math.max(0, dates.size() - (MAX_RECENT_INTERVALS + 1)), dates.size());
        long limit = (long) defaultIntervalDays * OUTLIER_MULTIPLIER;
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < recent.size(); i++) {
            long gap = ChronoUnit.DAYS.between(recent.get(i - 1), recent.get(i));
            if (gap <= limit) {
                intervals.add(gap);
            }
        }
        if (intervals.isEmpty()) {
            return new GroomInterval(defaultIntervalDays, GroomIntervalBasis.DEFAULT);
        }
        return new GroomInterval(median(intervals), GroomIntervalBasis.MEDIAN);
    }

    /** 다음 미용 예정일 = 마지막 미용일 + 주기 (POL-CYC-004) */
    public static LocalDate nextDueDate(LocalDate lastGroomDate, GroomInterval interval) {
        if (lastGroomDate == null || interval == null) {
            throw new IllegalArgumentException("마지막 미용일과 주기가 필요하다");
        }
        return lastGroomDate.plusDays(interval.days());
    }

    /** 중앙값. 개수가 짝수면 가운데 두 값의 평균을 반올림한다 [제안, #144에서 확정]. */
    static int median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n % 2 == 1) {
            return Math.toIntExact(sorted.get(n / 2));
        }
        long sum = sorted.get(n / 2 - 1) + sorted.get(n / 2);
        return Math.toIntExact((sum + 1) / 2);
    }
}
