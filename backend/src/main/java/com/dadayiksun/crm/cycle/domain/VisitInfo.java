package com.dadayiksun.crm.cycle.domain;

import java.time.LocalDate;

/** 방문 한 건 (visit에서 읽기만 함). 접종이면 vaccineTypeId가 있고 미용이면 null (DB 설계 4-9) */
public record VisitInfo(long visitId, long petId, ServiceType serviceType, Long vaccineTypeId, LocalDate visitDate) {
}
