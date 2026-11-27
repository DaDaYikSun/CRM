package com.dadayiksun.crm.cycle.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.dadayiksun.crm.cycle.domain.PetProfile;
import com.dadayiksun.crm.cycle.domain.ServiceType;
import com.dadayiksun.crm.cycle.domain.VaccineRule;
import com.dadayiksun.crm.cycle.domain.VisitInfo;

/**
 * 계산에 필요한 값을 읽는 조회 전용 쿼리. 쓰지 않는다.
 * pet · coat_type(customer) · visit(visit)은 주인이 아직 Reader를 만들지 않아 cycle 안 조회 쿼리로 읽는다 (#144 결정,
 * 다른 패키지 Entity · Repository는 import하지 않음, API 설계 4-1). 주인 Reader가 생기면 그쪽으로 바꾼다.
 * vaccine_type · vaccine_rule은 cycle 규칙이다.
 */
public interface CycleSourceQuery {

    Optional<PetProfile> petProfile(long petId);

    /** 그 아이 · 서비스(· 접종 종류)의 방문 날짜, 오래된 순 */
    List<LocalDate> visitDates(long petId, ServiceType serviceType, Long vaccineTypeId);

    Optional<VisitInfo> visit(long visitId);

    List<Long> activePetIds();

    /** 쓰는 접종 종류(is_active)별 쓰는 규칙 행. 규칙이 없는 종류는 빠진다 (예정을 만들지 않음) */
    Map<Long, List<VaccineRule>> activeRulesByType();

    /**
     * 선택 접종 종류 (#204, 접종 규칙 표 2장 8번): 기록이 있는 아이에게만 예정을 만든다.
     * 가정: 주요 접종이 아니고(is_major = 0) 규칙에 PREVENTIVE 단계 행이 하나도 없는 종류 (코로나 장염 · 켄넬코프 · 인플루엔자).
     * PREVENTIVE 행은 쓰는지(is_active)와 상관없이 본다 (행을 꺼도 분류가 바뀌지 않게).
     * 심장사상충 · 구충제(PREVENTIVE)와 주요 접종은 빠진다. vaccine_type에 칸을 더할지는 #175에서 다시 정한다.
     */
    Set<Long> selectiveTypeIds();
}
