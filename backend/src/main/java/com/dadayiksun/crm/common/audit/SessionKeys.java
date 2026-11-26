package com.dadayiksun.crm.common.audit;

/**
 * 세션에 넣는 값의 이름. 로그인(#143)이 로그인한 직원 ID를 {@link #STAFF_ID}로 넣으면
 * 감사 컬럼(created_by · updated_by)이 자동으로 채워진다 (NFR-DATA-002).
 */
public final class SessionKeys {

    public static final String STAFF_ID = "CRM_STAFF_ID";

    private SessionKeys() {
    }
}
