package com.dadayiksun.crm.cycle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

/** B-02 · B-03 저장 규칙 (FR-ADM-002 · 003 · 004, POL-ERR-006, POL-CYC-005, #175) */
class CycleRuleServiceTest {

    private static final VaccineTypeRow DHPPL = new VaccineTypeRow(1L, "DHPPL", "종합백신", true, true);
    private static final VaccineTypeRow HEART = new VaccineTypeRow(2L, "HEARTWORM", "심장사상충", false, true);
    private static final VaccineRuleRow DHPPL_BOOSTER = new VaccineRuleRow(10L, "BOOSTER", 365, null, 365, null, "매년");
    private static final VaccineRuleRow HEART_MONTHLY = new VaccineRuleRow(20L, "PREVENTIVE", 56, null, 30, null, null);

    private RuleRepository rules;
    private CycleService cycle;
    private SettingService settings;
    private RuleChangeLogWriter changeLog;
    private CycleRuleService service;

    @BeforeEach
    void setUp() {
        rules = mock(RuleRepository.class);
        cycle = mock(CycleService.class);
        settings = mock(SettingService.class);
        changeLog = mock(RuleChangeLogWriter.class);
        // 진짜 기록 도구처럼 바뀐 칸이 있을 때만 true
        when(changeLog.log(any(), any(), any(), any()))
                .thenAnswer(inv -> !inv.getArgument(2).equals(inv.getArgument(3)));
        when(changeLog.currentStaffId()).thenReturn(1L);
        when(rules.vaccineRules()).thenReturn(List.of(
                new VaccineRuleGroup(DHPPL, List.of(DHPPL_BOOSTER)),
                new VaccineRuleGroup(HEART, List.of(HEART_MONTHLY))));
        when(rules.coatIntervals()).thenReturn(List.of(new CoatInterval(1L, "단모", 10)));
        when(cycle.recalculateByRule()).thenReturn(118);
        service = new CycleRuleService(rules, cycle, settings, changeLog);
    }

    private static VaccineRuleGroup heart(int intervalDays) {
        return new VaccineRuleGroup(HEART, List.of(new VaccineRuleRow(20L, "PREVENTIVE", 56, null, intervalDays, null, null)));
    }

    @Test
    void 간격을_바꾸면_저장하고_이력을_남기고_다시_계산한다() {
        int count = service.saveVaccineRules(List.of(heart(28)));

        assertThat(count).isEqualTo(118);
        verify(rules).updateRule(new VaccineRuleRow(20L, "PREVENTIVE", 56, null, 28, null, null), 1L);
        verify(changeLog).log(eq("VACCINE_RULE"), eq("20"), any(), any());
        verify(cycle).recalculateByRule();
    }

    @Test
    void 값이_그대로면_저장도_다시_계산도_하지_않는다() {
        assertThat(service.saveVaccineRules(List.of(heart(30)))).isZero();

        verify(rules, never()).updateRule(any(), anyLong());
        verify(cycle, never()).recalculateByRule();
    }

    @Test
    void 간격이_0이거나_비면_RULE_VALUE_INVALID이고_아무것도_저장하지_않는다() {
        for (Integer bad : new Integer[] {0, -1, null}) {
            VaccineRuleGroup group = new VaccineRuleGroup(DHPPL, List.of(
                    new VaccineRuleRow(10L, "BOOSTER", 365, null, 300, null, null)));
            VaccineRuleGroup badGroup = new VaccineRuleGroup(HEART, List.of(
                    new VaccineRuleRow(20L, "PREVENTIVE", 56, null, bad, null, null)));
            assertThatThrownBy(() -> service.saveVaccineRules(List.of(group, badGroup)))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RULE_VALUE_INVALID));
        }
        verify(rules, never()).updateRule(any(), anyLong());
        verify(cycle, never()).recalculateByRule();
    }

    @Test
    void 끝_나이가_시작_나이보다_작거나_같으면_RULE_VALUE_INVALID() {
        VaccineRuleGroup g = new VaccineRuleGroup(HEART, List.of(new VaccineRuleRow(20L, "PREVENTIVE", 56, 56, 30, null, null)));
        assertThatThrownBy(() -> service.saveVaccineRules(List.of(g)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RULE_VALUE_INVALID));
    }

    @Test
    void 다른_종류의_규칙_행이면_NOT_FOUND() {
        VaccineRuleGroup g = new VaccineRuleGroup(DHPPL, List.of(HEART_MONTHLY));
        assertThatThrownBy(() -> service.saveVaccineRules(List.of(g)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void 주요_접종만_바꾸면_종류_행만_고치고_다시_계산하지_않는다() {
        VaccineTypeRow notMajor = new VaccineTypeRow(1L, "DHPPL", "종합백신", false, true);
        service.saveVaccineRules(List.of(new VaccineRuleGroup(notMajor, List.of())));

        verify(rules).updateMajor(1L, false, 1L);
        verify(cycle, never()).recalculateByRule();
    }

    @Test
    void 미용_간격을_바꾸면_다시_계산하고_0이면_막는다() {
        assertThat(service.saveCoatIntervals(List.of(new CoatInterval(1L, "단모", 8)))).isEqualTo(118);
        verify(rules).updateGroomWeeks(1L, 8, 1L);

        assertThatThrownBy(() -> service.saveCoatIntervals(List.of(new CoatInterval(1L, "단모", 0))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RULE_VALUE_INVALID));
    }

    @Test
    void 누락_기준은_저장만_하고_다시_계산하지_않는다() {
        when(settings.getInt(SettingKey.MISS_THRESHOLD_DAYS)).thenReturn(14);

        service.saveMissThresholdDays(10);

        verify(settings).updateInt(SettingKey.MISS_THRESHOLD_DAYS, 10, 1L);
        verify(cycle, never()).recalculateByRule();
    }

    @Test
    void 누락_기준이_비었거나_0이면_막는다() {
        for (Integer bad : new Integer[] {null, 0}) {
            assertThatThrownBy(() -> service.saveMissThresholdDays(bad))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RULE_VALUE_INVALID));
        }
        verify(settings, never()).updateInt(any(), anyInt(), anyLong());
    }
}
