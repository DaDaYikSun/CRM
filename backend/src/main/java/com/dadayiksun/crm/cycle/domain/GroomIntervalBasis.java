package com.dadayiksun.crm.cycle.domain;

/** 미용 주기를 어떻게 정했는지. DB `pet.groom_interval_basis`와 같다 (DB 설계 4-2). */
public enum GroomIntervalBasis {
    /** 털 유형 기본 간격 (POL-CYC-002) */
    DEFAULT,
    /** 최근 간격의 중앙값 (POL-CYC-003) */
    MEDIAN
}
