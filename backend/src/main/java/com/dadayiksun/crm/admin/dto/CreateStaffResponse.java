package com.dadayiksun.crm.admin.dto;

/** POST /api/admin/staff 출력 201. 임시 비밀번호는 이 응답에서만 한 번 보인다 (정책 4-4 B-01, POL-ACC-010). */
public record CreateStaffResponse(Long staffId, String tempPassword) {
}
