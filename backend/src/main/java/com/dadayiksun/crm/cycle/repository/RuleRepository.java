package com.dadayiksun.crm.cycle.repository;

import java.util.List;

import com.dadayiksun.crm.cycle.domain.RuleSettings.CoatInterval;
import com.dadayiksun.crm.cycle.domain.RuleSettings.VaccineRuleGroup;
import com.dadayiksun.crm.cycle.domain.RuleSettings.VaccineRuleRow;

/**
 * 접종 규칙 · 미용 기본 간격 읽기 · 쓰기 (C17, #175). 주인은 cycle (API 설계 3-10, 11/25 회의 R4).
 * coat_type 행 자체(이름 · 선택지)는 customer 몫이고, 여기서는 default_groom_weeks 칸만 고친다.
 */
public interface RuleRepository {

    /** 사용 중인 규칙 행만, 접종 종류 정렬 순서대로. 규칙이 없는 종류도 빈 목록으로 나온다 */
    List<VaccineRuleGroup> vaccineRules();

    void updateRule(VaccineRuleRow rule, long staffId);

    void updateMajor(long vaccineTypeId, boolean isMajor, long staffId);

    /** 사용 중인 털 유형만 */
    List<CoatInterval> coatIntervals();

    void updateGroomWeeks(long coatTypeId, int weeks, long staffId);
}
