package com.dadayiksun.crm.visit.dto;

import java.time.LocalDate;

/**
 * {@code POST /api/pets/{petId}/visits} 입력 (API 설계 3-5, C8).
 * 데려온 사람 번호({@code broughtByPhone})는 #178이 더한다.
 */
public record RecordVisitRequest(String serviceType, Long vaccineTypeId, LocalDate visitDate, String memo) {
}
