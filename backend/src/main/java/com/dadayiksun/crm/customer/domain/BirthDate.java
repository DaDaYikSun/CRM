package com.dadayiksun.crm.customer.domain;

import java.time.LocalDate;

/**
 * 저장할 생년월일과 '추정' 여부 (POL-REG-004).
 * 대략 나이로 받으면 오늘(한국 시간)에서 그만큼 뺀 날을 생년월일로 하고 추정으로 둔다.
 * 1살 미만은 개월 수, 그 이상은 나이로 받는다.
 */
public record BirthDate(LocalDate date, boolean estimated) {

    public static BirthDate exact(LocalDate date) {
        return new BirthDate(date, false);
    }

    public static BirthDate fromYears(LocalDate today, int years) {
        return new BirthDate(today.minusYears(years), true);
    }

    public static BirthDate fromMonths(LocalDate today, int months) {
        return new BirthDate(today.minusMonths(months), true);
    }
}
