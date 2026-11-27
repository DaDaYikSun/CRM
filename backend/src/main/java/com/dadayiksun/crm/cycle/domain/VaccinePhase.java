package com.dadayiksun.crm.cycle.domain;

/** 접종 규칙 단계. DB `vaccine_rule.phase`와 같다 (DB 설계 4-7). */
public enum VaccinePhase {
    /** 기초접종 */
    PRIMARY,
    /** 추가접종 */
    BOOSTER,
    /** 예방약 (심장사상충 · 구충) */
    PREVENTIVE
}
