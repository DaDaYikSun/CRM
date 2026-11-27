package com.dadayiksun.crm.auth.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 관리자 전용 표시 (FR-AUTH-002, POL-ACC-002). Controller 클래스나 메서드에 붙이면 관리자가 아닌 직원은 403.
 * 경로 규칙({@link RoleAccessRules})과 별개로 한 번 더 막는 장치다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AdminOnly {
}
