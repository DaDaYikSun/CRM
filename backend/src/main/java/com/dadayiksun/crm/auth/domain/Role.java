package com.dadayiksun.crm.auth.domain;

/** 역할 3가지 (POL-ACC-001). 원장과 데스크 실장은 같은 ADMIN이다. DB `staff.role` ENUM과 같은 이름. */
public enum Role {
    ADMIN, DESK, GROOMER
}
