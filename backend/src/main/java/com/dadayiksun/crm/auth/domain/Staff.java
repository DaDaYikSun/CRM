package com.dadayiksun.crm.auth.domain;

/** 로그인에 쓰는 직원 한 명 (테이블 staff, DB 설계 4-1). */
public record Staff(
        Long staffId,
        String loginId,
        String passwordHash,
        String name,
        Role role,
        boolean active,
        boolean mustChangePassword) {
}
