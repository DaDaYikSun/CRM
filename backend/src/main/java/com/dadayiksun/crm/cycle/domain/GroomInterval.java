package com.dadayiksun.crm.cycle.domain;

/**
 * 한 아이의 미용 주기 (FR-CYC-002). `pet.groom_interval_days` · `groom_interval_basis`에 저장한다.
 *
 * @param days  주기(일)
 * @param basis 기본 간격인지 중앙값인지
 */
public record GroomInterval(int days, GroomIntervalBasis basis) {

    public GroomInterval {
        if (days <= 0) {
            throw new IllegalArgumentException("미용 주기는 0보다 커야 한다");
        }
        if (basis == null) {
            throw new IllegalArgumentException("basis는 비어 있을 수 없다");
        }
    }
}
