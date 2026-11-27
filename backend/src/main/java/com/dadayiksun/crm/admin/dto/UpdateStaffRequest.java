package com.dadayiksun.crm.admin.dto;

import com.dadayiksun.crm.auth.domain.Role;

/** PATCH /api/admin/staff/{id} 입력: 바꿀 칸만 (API 설계 3-10). */
public record UpdateStaffRequest(Role role, Boolean isActive) {
}
