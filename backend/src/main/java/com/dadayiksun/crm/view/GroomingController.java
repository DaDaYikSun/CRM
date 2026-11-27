package com.dadayiksun.crm.view;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.web.PageQuery;

/**
 * 미용 예정 목록 (S-11, FR-VIS-002, API 설계 3장 {@code GET /api/grooming?tab=today|week&page=} [제안]).
 * 미용 예정일은 cycle 값이고 아이 · 보호자는 customer, 오늘 미용은 visit 값이라 조립 자리에 둔다 (API 설계 4-1 표).
 * 모든 역할이 읽는다 (RoleAccessRules, POL-ACC-011). 읽기만 한다.
 */
@RestController
@RequestMapping("/api/grooming")
public class GroomingController {

    private final GroomingListAssembler assembler;

    public GroomingController(GroomingListAssembler assembler) {
        this.assembler = assembler;
    }

    @GetMapping
    public ApiResponse<PageResponse<GroomingListAssembler.Line>> list(@RequestParam(required = false) String tab,
            @RequestParam(required = false) String page) {
        return ApiResponse.of(assembler.assemble(GroomingTab.parse(tab), PageQuery.parse(page)));
    }
}
