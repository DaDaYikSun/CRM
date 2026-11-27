package com.dadayiksun.crm.admin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.admin.dto.CreateStaffRequest;
import com.dadayiksun.crm.admin.dto.CreateStaffResponse;
import com.dadayiksun.crm.admin.dto.ResetPasswordResponse;
import com.dadayiksun.crm.admin.dto.StaffItem;
import com.dadayiksun.crm.admin.dto.UpdateStaffRequest;
import com.dadayiksun.crm.auth.access.AdminOnly;
import com.dadayiksun.crm.auth.service.StaffAccountService;
import com.dadayiksun.crm.common.response.ApiResponse;

import jakarta.validation.Valid;

/**
 * B-01 직원 관리 (FR-ADM-001 · FR-ADM-007, 계약 C16, API 설계 3-10). 관리자만 (FR-AUTH-002, NFR-SEC-002).
 * 화면용 Controller만 둔다. 저장 규칙은 auth의 {@link StaffAccountService} (팀 결정 K14, API 설계 4-2).
 */
@RestController
@AdminOnly
@RequestMapping("/api/admin/staff")
public class StaffAdminController {

    private final StaffAccountService staffAccountService;

    public StaffAdminController(StaffAccountService staffAccountService) {
        this.staffAccountService = staffAccountService;
    }

    @GetMapping
    public ApiResponse<List<StaffItem>> list() {
        return ApiResponse.of(staffAccountService.list().stream().map(StaffItem::from).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateStaffResponse> create(@Valid @RequestBody CreateStaffRequest body) {
        StaffAccountService.Created created = staffAccountService.create(body.loginId(), body.name(), body.role());
        return ApiResponse.of(new CreateStaffResponse(created.staffId(), created.tempPassword()));
    }

    @PatchMapping("/{staffId}")
    public ApiResponse<Void> update(@PathVariable Long staffId, @RequestBody UpdateStaffRequest body) {
        staffAccountService.update(staffId, body.role(), body.isActive());
        return ApiResponse.of(null);
    }

    /** 비밀번호 초기화 (FR-ADM-007, #183). 임시 비밀번호는 이 응답에서만 한 번 보인다. */
    @PostMapping("/{staffId}/reset-password")
    public ApiResponse<ResetPasswordResponse> resetPassword(@PathVariable Long staffId) {
        return ApiResponse.of(new ResetPasswordResponse(staffAccountService.resetPassword(staffId)));
    }
}
