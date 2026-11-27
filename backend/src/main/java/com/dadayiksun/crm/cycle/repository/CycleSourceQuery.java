package com.dadayiksun.crm.cycle.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
}
