package com.dadayiksun.crm.admin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.auth.access.AdminOnly;
import com.dadayiksun.crm.common.audit.RuleHistoryQuery;
import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.customer.dto.OptionAdminDtos.BreedInput;
import com.dadayiksun.crm.customer.dto.OptionAdminDtos.BreedItem;
import com.dadayiksun.crm.customer.dto.OptionAdminDtos.CoatTypeInput;
import com.dadayiksun.crm.customer.dto.OptionAdminDtos.CoatTypeItem;
import com.dadayiksun.crm.customer.service.OptionAdminService;

/**
 * B-03 선택지 관리 · B-06 설정 변경 이력 (FR-ADM-005 · 006, API 설계 3-10, #182). 관리자만 (POL-ACC-002, NFR-SEC-002).
 * 화면용 Controller만 둔다. 선택지 저장 규칙은 customer, 이력 조회는 common의 조회 전용 쿼리 (R4, K17).
 */
@RestController
@AdminOnly
@RequestMapping("/api/admin")
public class OptionAdminController {

    private final OptionAdminService options;
    private final RuleHistoryQuery history;

    public OptionAdminController(OptionAdminService options, RuleHistoryQuery history) {
        this.options = options;
        this.history = history;
    }

    @GetMapping("/breeds")
    public ApiResponse<List<BreedItem>> breeds() {
        return ApiResponse.of(options.breeds());
    }

    @PostMapping("/breeds")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BreedItem> createBreed(@RequestBody BreedInput body) {
        return ApiResponse.of(options.createBreed(body));
    }

    @PatchMapping("/breeds/{breedId}")
    public ApiResponse<BreedItem> updateBreed(@PathVariable long breedId, @RequestBody BreedInput body) {
        return ApiResponse.of(options.updateBreed(breedId, body));
    }

    @GetMapping("/coat-types")
    public ApiResponse<List<CoatTypeItem>> coatTypes() {
        return ApiResponse.of(options.coatTypes());
    }

    @PostMapping("/coat-types")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CoatTypeItem> createCoatType(@RequestBody CoatTypeInput body) {
        return ApiResponse.of(options.createCoatType(body));
    }

    @PatchMapping("/coat-types/{coatTypeId}")
    public ApiResponse<CoatTypeItem> updateCoatType(@PathVariable long coatTypeId, @RequestBody CoatTypeInput body) {
        return ApiResponse.of(options.updateCoatType(coatTypeId, body));
    }

    @GetMapping("/rule-history")
    public ApiResponse<PageResponse<RuleHistoryQuery.Item>> ruleHistory(@RequestParam(required = false) String page) {
        return ApiResponse.of(history.page(PageQuery.parse(page)));
    }
}
