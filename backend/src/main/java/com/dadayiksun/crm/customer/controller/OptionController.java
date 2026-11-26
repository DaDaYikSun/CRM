package com.dadayiksun.crm.customer.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.customer.dto.OptionResponses.BreedOption;
import com.dadayiksun.crm.customer.dto.OptionResponses.CoatTypeOption;
import com.dadayiksun.crm.customer.dto.OptionResponses.VaccineTypeOption;
import com.dadayiksun.crm.customer.service.OptionQueryService;

/** 계약 C5 선택지 (API 설계 3-2). 모든 역할 허용 (API 설계 2장). */
@RestController
@RequestMapping("/api/options")
public class OptionController {

    private final OptionQueryService options;

    public OptionController(OptionQueryService options) {
        this.options = options;
    }

    @GetMapping("/breeds")
    public ApiResponse<List<BreedOption>> breeds() {
        return ApiResponse.of(options.breeds());
    }

    @GetMapping("/coat-types")
    public ApiResponse<List<CoatTypeOption>> coatTypes() {
        return ApiResponse.of(options.coatTypes());
    }

    @GetMapping("/vaccine-types")
    public ApiResponse<List<VaccineTypeOption>> vaccineTypes() {
        return ApiResponse.of(options.vaccineTypes());
    }
}
