package com.dadayiksun.crm.visit.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 아이의 방문 한 줄 (통합 타임라인 C9 visit 몫, API 설계 3-5).
 *
 * @param summary 접종이면 접종 종류 이름, 미용이면 "미용"
 */
public record VisitLine(long id, String serviceType, LocalDate date, String summary, String memo, String staffName,
                        OffsetDateTime recordedAt, boolean edited) {
}
