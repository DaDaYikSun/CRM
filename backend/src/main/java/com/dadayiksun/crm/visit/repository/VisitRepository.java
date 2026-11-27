package com.dadayiksun.crm.visit.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * visit 테이블 쓰기와 저장 검사에 필요한 조회 (C8 · C9).
 * pet(customer) · vaccine_type(cycle 규칙) · staff(auth)는 읽기만 한다 (K17 ②).
 */
public interface VisitRepository {

    /** 아이 상태(ACTIVE · RAINBOW). 없는 아이면 빈 값 */
    Optional<String> petStatus(long petId);

    /** 지금 고를 수 있는 접종 종류인지 (있고 is_active = 1, 선택지 C5와 같은 조건) */
    boolean isSelectableVaccineType(long vaccineTypeId);

    /** 같은 아이 · 같은 날 · 같은 서비스(접종이면 같은 종류) 기록이 이미 있는지 (#148 결정) */
    boolean existsSameDay(long petId, String serviceType, Long vaccineTypeId, LocalDate visitDate);

    /** 새 visit_id */
    long insert(Row row);

    /** 아이의 방문 줄, 서비스 종류로 거른다. 순서는 조립 자리가 정한다 */
    List<LineRow> lines(long petId, Collection<String> serviceTypes);

    record Row(long petId, String serviceType, Long vaccineTypeId, LocalDate visitDate, String memo, long createdBy,
               LocalDateTime createdAt) {
    }

    record LineRow(long visitId, String serviceType, String vaccineTypeName, LocalDate visitDate, String memo,
                   String staffName, LocalDateTime createdAt, boolean edited) {
    }
}
