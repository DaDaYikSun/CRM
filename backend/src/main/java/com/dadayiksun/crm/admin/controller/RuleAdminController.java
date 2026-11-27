package com.dadayiksun.crm.admin.controller;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dadayiksun.crm.auth.access.AdminOnly;
import com.dadayiksun.crm.common.response.ApiResponse;
import com.dadayiksun.crm.customer.service.RainbowHoldSettingService;
import com.dadayiksun.crm.cycle.domain.RuleSettings.CoatInterval;
import com.dadayiksun.crm.cycle.domain.RuleSettings.VaccineRuleGroup;
import com.dadayiksun.crm.cycle.service.CycleRuleService;

/**
 * B-02 주기 규칙 · B-03 기준값 (FR-ADM-002 · 003 · 004, 계약 C17, API 설계 3-10, #175). 관리자만 (POL-ACC-002, NFR-SEC-002).
 * 화면용 Controller만 둔다. 저장 규칙은 cycle(접종 규칙 · 미용 간격 · 누락 기준)과 customer(연락 보류 기간)가 가진다 (R4).
 */
@RestController
@AdminOnly
@RequestMapping("/api/admin")
public class RuleAdminController {

    private final CycleRuleService cycleRules;
    private final RainbowHoldSettingService rainbowHold;

    public RuleAdminController(CycleRuleService cycleRules, RainbowHoldSettingService rainbowHold) {
        this.cycleRules = cycleRules;
        this.rainbowHold = rainbowHold;
    }

    @GetMapping("/vaccine-rules")
    public ApiResponse<List<VaccineRuleGroup>> vaccineRules() {
        return ApiResponse.of(cycleRules.vaccineRules());
    }

    /** 고친 종류만 받는다. 출력은 다시 계산한 아이 수 */
    @PutMapping("/vaccine-rules")
    public ApiResponse<Recalculated> saveVaccineRules(@RequestBody List<VaccineRuleGroup> body) {
        return ApiResponse.of(new Recalculated(cycleRules.saveVaccineRules(body)));
    }

    @GetMapping("/coat-types/intervals")
    public ApiResponse<List<CoatInterval>> coatIntervals() {
        return ApiResponse.of(cycleRules.coatIntervals());
    }

    @PutMapping("/coat-types/intervals")
    public ApiResponse<Recalculated> saveCoatIntervals(@RequestBody List<CoatInterval> body) {
        return ApiResponse.of(new Recalculated(cycleRules.saveCoatIntervals(body)));
    }

    @GetMapping("/settings")
    public ApiResponse<Settings> settings() {
        return ApiResponse.of(new Settings(cycleRules.missThresholdDays(), rainbowHold.rainbowHoldDays()));
    }

    /** 두 값을 한 트랜잭션으로: 하나가 틀리면 둘 다 저장하지 않는다 (POL-ERR-006) */
    @PutMapping("/settings")
    @Transactional
    public ApiResponse<Settings> saveSettings(@RequestBody Settings body) {
        cycleRules.saveMissThresholdDays(body.missThresholdDays());
        rainbowHold.save(body.rainbowHoldDays());
        return settings();
    }

    public record Recalculated(int recalculatedPetCount) {
    }

    public record Settings(Integer missThresholdDays, Integer rainbowHoldDays) {
    }
}
