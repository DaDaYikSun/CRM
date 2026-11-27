package com.dadayiksun.crm.cycle.domain;

import java.util.List;

/**
 * B-02 주기 규칙 화면이 주고받는 값 (C17, API 설계 3-10). 칸 구조는 DB 설계 4-7 {@code vaccine_rule}, 4-3 {@code coat_type} 그대로.
 * 입력은 비었을 수 있어 감싼 타입이다. 검사는 {@code CycleRuleService}가 한다 (POL-ERR-006).
 */
public final class RuleSettings {

    private RuleSettings() {
    }

    /** 접종 종류 하나와 그 규칙 행들. PUT은 고친 종류만 보낸다 */
    public record VaccineRuleGroup(VaccineTypeRow vaccineType, List<VaccineRuleRow> rules) {
    }

    /** 바꿀 수 있는 칸은 isMajor('주요 접종', 화면 설계 B-02 안내)뿐. 이름 · 사용 여부는 #182 선택지 몫 */
    public record VaccineTypeRow(Long vaccineTypeId, String code, String name, Boolean isMajor, Boolean isActive) {
    }

    /** ageToDays · doseCount가 비면 '끝 없음' · '계속 반복' (DB 4-7). note는 근거 메모라 화면에서 고치지 않는다 */
    public record VaccineRuleRow(Long vaccineRuleId, String phase, Integer ageFromDays, Integer ageToDays,
                                 Integer intervalDays, Integer doseCount, String note) {
    }

    /** 털 유형별 미용 기본 간격(주). 미용 기록 2회 미만인 아이에게만 쓰인다 (POL-CYC-002) */
    public record CoatInterval(Long coatTypeId, String name, Integer defaultGroomWeeks) {
    }
}
