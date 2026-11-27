package com.dadayiksun.crm.admin.dto;

/** POST /api/admin/staff/{id}/reset-password 출력. 임시 비밀번호는 이 응답에서만 한 번 보인다 (POL-ACC-010, #183). */
public record ResetPasswordResponse(String tempPassword) {
}
