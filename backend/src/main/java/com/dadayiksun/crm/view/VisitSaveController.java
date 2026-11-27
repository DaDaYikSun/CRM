package com.dadayiksun.crm.view;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.visit.dto.RecordVisitRequest;

/** 계약 C8 방문 기록 (S-09, API 설계 3-5, FR-VIS-001 · 002). 출력이 cycle 값을 합치므로 조립 자리에 둔다 (4-1). */
@RestController
public class VisitSaveController {

    private final VisitSaveAssembler assembler;

    public VisitSaveController(VisitSaveAssembler assembler) {
        this.assembler = assembler;
    }

    @PostMapping("/api/pets/{petId}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VisitSaveAssembler.Response> record(@PathVariable("petId") long petId,
                                                           @RequestBody RecordVisitRequest body, CurrentStaff staff) {
        return ApiResponse.of(assembler.record(petId, body, staff));
    }
}
