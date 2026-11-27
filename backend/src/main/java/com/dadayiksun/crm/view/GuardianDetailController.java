package com.dadayiksun.crm.view;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.customer.service.GuardianUpdateService;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 계약 C10 보호자 상세 · 수정 (S-06, API 설계 3-3, FR-CUS-008). 두 API 모두 같은 모양을 주므로 조립 자리에 둔다.
 *
 * <p>[막힘] 역할 403(미용사 거부, POL-ACC-011)은 아직 걸지 않았다. #143이 API 설계 2장 허용 표를 한 곳에 옮기면 걸린다.
 * 전체 번호를 주는 API라 #143 전에는 배포하면 안 된다 (NFR-SEC-003).
 */
@RestController
@RequestMapping("/api/guardians")
public class GuardianDetailController {

    private final GuardianDetailAssembler assembler;
    private final GuardianUpdateService update;

    public GuardianDetailController(GuardianDetailAssembler assembler, GuardianUpdateService update) {
        this.assembler = assembler;
        this.update = update;
    }

    @GetMapping("/{guardianId}")
    public ApiResponse<GuardianDetailAssembler.Response> detail(@PathVariable("guardianId") Long guardianId) {
        return ApiResponse.of(assembler.assemble(guardianId));
    }

    /** 바꿀 칸만 받는다. 칸이 없으면 그대로, null이면 비운다 (API 설계 3-3). 저장이 끝난 뒤 상세를 다시 읽어 준다. */
    @PatchMapping("/{guardianId}")
    public ApiResponse<GuardianDetailAssembler.Response> patch(@PathVariable("guardianId") Long guardianId,
            @RequestBody JsonNode body) {
        update.update(guardianId, body);
        return ApiResponse.of(assembler.assemble(guardianId));
    }
}
