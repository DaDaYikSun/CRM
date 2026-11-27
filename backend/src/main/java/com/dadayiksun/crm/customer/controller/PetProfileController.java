package com.dadayiksun.crm.customer.controller;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.customer.dto.PetProfilePatch;
import com.dadayiksun.crm.customer.dto.PetProfileResponse;
import com.dadayiksun.crm.customer.dto.RainbowResponse;
import com.dadayiksun.crm.customer.dto.RainbowRevertResponse;
import com.dadayiksun.crm.customer.service.PetProfileService;
import com.dadayiksun.crm.customer.service.RainbowMarkService;
import com.dadayiksun.crm.customer.service.RainbowRevertService;

/**
 * 계약 C10 아이 프로필 수정 · 무지개다리 되돌리기 (API 설계 3-3, FR-CUS-008).
 *
 * <p>역할 403은 #143의 RoleAccessRules가 건다: 수정은 관리자 · 데스크(미용사 403), 되돌리기는 관리자만 (POL-ACC-011, POL-END-004).
 */
@RestController
@RequestMapping("/api/pets")
public class PetProfileController {

    private final PetProfileService profile;
    private final RainbowRevertService rainbowRevert;
    private final RainbowMarkService rainbowMark;

    public PetProfileController(PetProfileService profile, RainbowRevertService rainbowRevert,
            RainbowMarkService rainbowMark) {
        this.profile = profile;
        this.rainbowRevert = rainbowRevert;
        this.rainbowMark = rainbowMark;
    }

    /** C21 무지개다리로 표시. 관리자 · 데스크만 (POL-ACC-011, 역할은 RoleAccessRules가 건다). */
    @PostMapping("/{petId}/rainbow")
    public ApiResponse<RainbowResponse> rainbow(@PathVariable("petId") Long petId) {
        return ApiResponse.of(rainbowMark.mark(petId));
    }

    @PatchMapping("/{petId}")
    public ApiResponse<PetProfileResponse> update(@PathVariable("petId") Long petId,
            @RequestBody PetProfilePatch patch) {
        return ApiResponse.of(profile.update(petId, patch));
    }

    @PostMapping("/{petId}/rainbow-revert")
    public ApiResponse<RainbowRevertResponse> revert(@PathVariable("petId") Long petId) {
        return ApiResponse.of(rainbowRevert.revert(petId));
    }
}
