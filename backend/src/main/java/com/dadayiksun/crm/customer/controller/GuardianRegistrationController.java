package com.dadayiksun.crm.customer.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.customer.dto.AddPetRequest;
import com.dadayiksun.crm.customer.dto.AddPetResponse;
import com.dadayiksun.crm.customer.dto.PhoneCheckResponse;
import com.dadayiksun.crm.customer.dto.RegisterGuardianRequest;
import com.dadayiksun.crm.customer.dto.RegisterGuardianResponse;
import com.dadayiksun.crm.customer.service.PetRegistrationService;
import com.dadayiksun.crm.customer.service.PhoneCheckService;

import jakarta.validation.Valid;

/**
 * 계약 C6 번호 확인 · 등록 (API 설계 3-3, S-08). 관리자 · 데스크만 (API 설계 2장, POL-ACC-011).
 *
 * <p>[막힘] 미용사 403은 아직 걸지 않았다. #142에 역할 검사 방법이 없고, 세션에는 직원 ID만 있다.
 * #143(로그인 · 역할)이 정한 방법으로 이 Controller의 세 API에 더한다.
 * 저장 두 번 막기는 {@code IdempotencyFilter}(#142, K15)가 이 주소들 앞에서 한다.
 */
@RestController
@RequestMapping("/api/guardians")
public class GuardianRegistrationController {

    private final PhoneCheckService phoneCheck;
    private final PetRegistrationService registration;

    public GuardianRegistrationController(PhoneCheckService phoneCheck, PetRegistrationService registration) {
        this.phoneCheck = phoneCheck;
        this.registration = registration;
    }

    @GetMapping("/phone-check")
    public ApiResponse<PhoneCheckResponse> phoneCheck(@RequestParam("phone") String phone) {
        return ApiResponse.of(phoneCheck.check(phone));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterGuardianResponse> register(@Valid @RequestBody RegisterGuardianRequest request) {
        return ApiResponse.of(registration.registerGuardian(request));
    }

    @PostMapping("/{guardianId}/pets")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddPetResponse> addPet(@PathVariable("guardianId") Long guardianId,
            @Valid @RequestBody AddPetRequest request) {
        return ApiResponse.of(registration.addPet(guardianId, request.pet()));
    }
}
