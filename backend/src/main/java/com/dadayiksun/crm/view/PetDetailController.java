package com.dadayiksun.crm.view;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.common.response.PageResponse;

/**
 * 계약 C9 아이 상세 · 통합 타임라인 (S-07, API 설계 3-3 · 3-5, FR-VIS-003 · FR-CUS-003).
 * 두 API 모두 여러 도메인을 합치므로 조립 자리에 둔다. 상세는 모든 역할이 읽는다 (전체 번호 없음, NFR-SEC-003).
 * 타임라인의 미용사 제한은 규칙(#143)이 {@code type=VACCINE|CONTACT}를 막고, 나머지는 조립 자리가 한다 (POL-ACC-011).
 */
@RestController
@RequestMapping("/api/pets")
public class PetDetailController {

    private final PetDetailAssembler detail;
    private final PetTimelineAssembler timeline;

    public PetDetailController(PetDetailAssembler detail, PetTimelineAssembler timeline) {
        this.detail = detail;
        this.timeline = timeline;
    }

    @GetMapping("/{petId}")
    public ApiResponse<PetDetailAssembler.Response> detail(@PathVariable("petId") Long petId) {
        return ApiResponse.of(detail.assemble(petId));
    }

    @GetMapping("/{petId}/timeline")
    public ApiResponse<PageResponse<TimelineLine>> timeline(CurrentStaff staff, @PathVariable("petId") Long petId,
            @RequestParam(required = false) String type, @RequestParam(required = false) String page) {
        return ApiResponse.of(timeline.assemble(staff, petId, type, page));
    }
}
