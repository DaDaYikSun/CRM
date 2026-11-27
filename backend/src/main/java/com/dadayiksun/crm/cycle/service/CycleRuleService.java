package com.dadayiksun.crm.cycle.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.audit.RuleChangeLogWriter;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.setting.SettingKey;
import com.dadayiksun.crm.common.setting.SettingService;
import com.dadayiksun.crm.cycle.domain.RuleSettings.CoatInterval;
import com.dadayiksun.crm.cycle.domain.RuleSettings.VaccineRuleGroup;
import com.dadayiksun.crm.cycle.domain.RuleSettings.VaccineRuleRow;
import com.dadayiksun.crm.cycle.domain.RuleSettings.VaccineTypeRow;
import com.dadayiksun.crm.cycle.repository.RuleRepository;

/**
 * B-02 주기 규칙 · B-03 누락 기준 저장 (C17, FR-ADM-002 · 003 · 004, #175). 저장 규칙의 주인은 cycle (API 설계 3-10, R4).
 *
 * <ul>
 *   <li>숫자 칸이 비었거나 0 이하면 {@code RULE_VALUE_INVALID}, 하나라도 틀리면 아무것도 저장하지 않는다 (POL-ERR-006)</li>
 *   <li>접종 규칙 · 주요 접종 · 미용 간격을 바꾸면 아직 오지 않은 예정일만 다시 계산한다 (C4, POL-CYC-005).
 *       주요 접종도 넣는 것은 선택 접종 판정(is_major = 0)이 예정을 만들지 정하기 때문 (#204, PR #246 리뷰)</li>
 *   <li>한 요청에 같은 ID가 두 번 오면 VALIDATION_FAILED. 이력의 '바꾸기 전 값'이 어긋나지 않게 (PR #246 리뷰)</li>
 *   <li>누락 기준은 이제부터 여는 주기에만 쓰여 다시 계산하지 않는다 (POL-MISS-001, DB 4-10)</li>
 *   <li>바뀐 값은 {@code change_log}에 RULE_CHANGE로, 바꾼 직원은 행의 updated_by에도 (FR-ADM-006, POL-DATA-008)</li>
 * </ul>
 */
@Service
@Transactional
public class CycleRuleService {

    // shortcut: 위쪽 한계는 날짜 계산이 넘치지 않게 막는 값일 뿐 정책 값이 아니다. 정책 담당이 범위를 정하면 바꾼다
    static final int MAX_DAYS = 36_500;
    /** coat_type.default_groom_weeks · vaccine_rule.dose_count는 TINYINT (DB 4-3 · 4-7) */
    static final int MAX_TINYINT = 127;

    private final RuleRepository rules;
    private final CycleService cycle;
    private final SettingService settings;
    private final RuleChangeLogWriter changeLog;

    public CycleRuleService(RuleRepository rules, CycleService cycle, SettingService settings,
                            RuleChangeLogWriter changeLog) {
        this.rules = rules;
        this.cycle = cycle;
        this.settings = settings;
        this.changeLog = changeLog;
    }

    @Transactional(readOnly = true)
    public List<VaccineRuleGroup> vaccineRules() {
        return rules.vaccineRules();
    }

    /** @return 다시 계산한 아이 수. 바뀐 값이 없으면 0 */
    public int saveVaccineRules(List<VaccineRuleGroup> input) {
        requireUnique(input.stream().map(g -> g.vaccineType() == null ? null : g.vaccineType().vaccineTypeId()).toList());
        requireUnique(input.stream().flatMap(g -> g.rules() == null ? Stream.<VaccineRuleRow>empty() : g.rules().stream())
                .map(VaccineRuleRow::vaccineRuleId).toList());
        Map<Long, VaccineTypeRow> types = new HashMap<>();
        Map<Long, VaccineRuleRow> current = new HashMap<>();
        Map<Long, Long> typeOfRule = new HashMap<>();
        for (VaccineRuleGroup g : rules.vaccineRules()) {
            types.put(g.vaccineType().vaccineTypeId(), g.vaccineType());
            for (VaccineRuleRow r : g.rules()) {
                current.put(r.vaccineRuleId(), r);
                typeOfRule.put(r.vaccineRuleId(), g.vaccineType().vaccineTypeId());
            }
        }
        // 먼저 모두 검사한다. 하나라도 틀리면 아무것도 저장하지 않는다
        for (VaccineRuleGroup g : input) {
            Long typeId = g.vaccineType() == null ? null : g.vaccineType().vaccineTypeId();
            if (typeId == null || !types.containsKey(typeId)) {
                throw new ApiException(ErrorCode.NOT_FOUND);
            }
            for (VaccineRuleRow r : g.rules() == null ? List.<VaccineRuleRow>of() : g.rules()) {
                if (r.vaccineRuleId() == null || !typeId.equals(typeOfRule.get(r.vaccineRuleId()))) {
                    throw new ApiException(ErrorCode.NOT_FOUND);
                }
                validate(r);
            }
        }

        long staffId = changeLog.currentStaffId();
        boolean changed = false;
        for (VaccineRuleGroup g : input) {
            VaccineTypeRow before = types.get(g.vaccineType().vaccineTypeId());
            Boolean major = g.vaccineType().isMajor();
            if (major != null && changeLog.log("VACCINE_TYPE", String.valueOf(before.vaccineTypeId()),
                    Map.of("isMajor", before.isMajor()), Map.of("isMajor", major))) {
                rules.updateMajor(before.vaccineTypeId(), major, staffId);
                changed = true;
            }
            for (VaccineRuleRow r : g.rules() == null ? List.<VaccineRuleRow>of() : g.rules()) {
                if (changeLog.log("VACCINE_RULE", String.valueOf(r.vaccineRuleId()),
                        values(current.get(r.vaccineRuleId())), values(r))) {
                    rules.updateRule(r, staffId);
                    changed = true;
                }
            }
        }
        return changed ? cycle.recalculateByRule() : 0;
    }

    @Transactional(readOnly = true)
    public List<CoatInterval> coatIntervals() {
        return rules.coatIntervals();
    }

    /** @return 다시 계산한 아이 수. 간격이 안 바뀌었으면 0 */
    public int saveCoatIntervals(List<CoatInterval> input) {
        requireUnique(input.stream().map(CoatInterval::coatTypeId).toList());
        Map<Long, CoatInterval> current = new HashMap<>();
        rules.coatIntervals().forEach(c -> current.put(c.coatTypeId(), c));
        for (CoatInterval c : input) {
            if (c.coatTypeId() == null || !current.containsKey(c.coatTypeId())) {
                throw new ApiException(ErrorCode.NOT_FOUND);
            }
            positive(c.defaultGroomWeeks(), MAX_TINYINT);
        }
        long staffId = changeLog.currentStaffId();
        boolean changed = false;
        for (CoatInterval c : input) {
            if (changeLog.log("COAT_TYPE", String.valueOf(c.coatTypeId()),
                    Map.of("defaultGroomWeeks", current.get(c.coatTypeId()).defaultGroomWeeks()),
                    Map.of("defaultGroomWeeks", c.defaultGroomWeeks()))) {
                rules.updateGroomWeeks(c.coatTypeId(), c.defaultGroomWeeks(), staffId);
                changed = true;
            }
        }
        return changed ? cycle.recalculateByRule() : 0;
    }

    @Transactional(readOnly = true)
    public int missThresholdDays() {
        return settings.getInt(SettingKey.MISS_THRESHOLD_DAYS);
    }

    /** 이미 열린 주기의 miss_threshold_days는 그대로다 (POL-MISS-001). 다시 계산하지 않는다 */
    public void saveMissThresholdDays(Integer days) {
        int value = positive(days, MAX_DAYS);
        if (changeLog.log("SETTING", SettingKey.MISS_THRESHOLD_DAYS,
                Map.of("value", missThresholdDays()), Map.of("value", value))) {
            settings.updateInt(SettingKey.MISS_THRESHOLD_DAYS, value, changeLog.currentStaffId());
        }
    }

    static void validate(VaccineRuleRow r) {
        int from = positive(r.ageFromDays(), MAX_DAYS);
        positive(r.intervalDays(), MAX_DAYS);
        // 끝 나이 · 횟수는 비면 '끝 없음' · '계속 반복'이라 비어도 된다 (DB 4-7). 채웠으면 1 이상
        if (r.ageToDays() != null && positive(r.ageToDays(), MAX_DAYS) <= from) {
            throw new ApiException(ErrorCode.RULE_VALUE_INVALID); // DB CHECK ck_vaccine_rule_age
        }
        if (r.doseCount() != null) {
            positive(r.doseCount(), MAX_TINYINT);
        }
    }

    /** 같은 ID가 두 번 오면 VALIDATION_FAILED (null은 뒤의 NOT_FOUND 검사가 막는다) */
    static void requireUnique(List<Long> ids) {
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (id != null && !seen.add(id)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FALLBACK_MESSAGE);
            }
        }
    }

    /** 비었거나 0 이하면 RULE_VALUE_INVALID (FR-ADM-004, POL-ERR-006) */
    static int positive(Integer value, int max) {
        if (value == null || value <= 0 || value > max) {
            throw new ApiException(ErrorCode.RULE_VALUE_INVALID);
        }
        return value;
    }

    private static Map<String, Object> values(VaccineRuleRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ageFromDays", r.ageFromDays());
        m.put("ageToDays", r.ageToDays());
        m.put("intervalDays", r.intervalDays());
        m.put("doseCount", r.doseCount());
        return m;
    }
}
