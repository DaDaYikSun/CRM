package com.dadayiksun.crm.auth.dto;

import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;

/** 로그인 · 현재 직원(me) 출력 (계약 C3, API 설계 3-1). */
public record LoginResponse(Long staffId, String name, Role role, boolean mustChangePassword) {

    public static LoginResponse from(Staff staff) {
        return new LoginResponse(staff.staffId(), staff.name(), staff.role(), staff.mustChangePassword());
    }
}
