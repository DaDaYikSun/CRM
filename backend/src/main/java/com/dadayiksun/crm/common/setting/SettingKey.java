package com.dadayiksun.crm.common.setting;

/**
 * setting 테이블의 키 (DB 설계 4-8, 계약 C2). 정책 숫자는 코드에 박지 않고 여기서 읽는다 (CLAUDE.md 3번).
 */
public final class SettingKey {

    /** 누락 기준 일수 (POL-MISS-001). */
    public static final String MISS_THRESHOLD_DAYS = "MISS_THRESHOLD_DAYS";
    /** 무지개다리 뒤 같은 집 연락 보류 일수 (POL-TGT-003). */
    public static final String RAINBOW_HOLD_DAYS = "RAINBOW_HOLD_DAYS";
    /** 병원 이름 (FR-ADM-008). */
    public static final String HOSPITAL_NAME = "HOSPITAL_NAME";
    /** 병원 전화번호, 숫자만 (FR-ADM-008). */
    public static final String HOSPITAL_PHONE = "HOSPITAL_PHONE";
    /** CRM 사용 전 · 후 비교 기준일 (FR-DATA-002). */
    public static final String CRM_START_DATE = "CRM_START_DATE";

    private SettingKey() {
    }
}
