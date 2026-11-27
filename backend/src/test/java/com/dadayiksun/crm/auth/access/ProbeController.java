package com.dadayiksun.crm.auth.access;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.common.response.ApiResponse;

/** 다른 이슈의 Controller 자리에 두는 시험용 주소들 (RoleAccessFilterTest). */
@RestController
class ProbeController {

    @GetMapping("/api/guardians/{id}")
    ApiResponse<String> guardian() {
        return ApiResponse.of("full-phone");
    }

    @GetMapping("/api/pets/{id}")
    ApiResponse<String> pet() {
        return ApiResponse.of("pet");
    }

    @PostMapping("/api/contacts")
    ApiResponse<String> contact() {
        return ApiResponse.of("contact");
    }

    @GetMapping("/api/admin/ping")
    ApiResponse<String> admin() {
        return ApiResponse.of("admin");
    }

    /** 경로 규칙에는 없고 @AdminOnly만 붙은 주소 */
    @AdminOnly
    @GetMapping("/api/only-admin-mark")
    ApiResponse<String> marked() {
        return ApiResponse.of("marked");
    }

    @GetMapping("/api/who")
    ApiResponse<Long> who(CurrentStaff staff) {
        return ApiResponse.of(staff.staffId());
    }
}
