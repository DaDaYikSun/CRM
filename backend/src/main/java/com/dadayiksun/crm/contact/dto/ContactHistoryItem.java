package com.dadayiksun.crm.contact.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code GET /api/guardians/{id}/contacts} 한 줄: 타임라인 {@code CONTACT} 줄 모양 + {@code petName} (API 설계 3-5 · 3-6).
 *
 * @param summary  "전화 · 본인 · 전화 안 받음"
 * @param editable 로그인한 직원이 오늘 남긴 기록인지 (POL-DATA-006). 수정 화면은 초안 28
 */
public record ContactHistoryItem(String kind, long id, LocalDate date, String summary, String memo, String staffName,
                                 OffsetDateTime recordedAt, boolean edited, boolean editable, boolean converted,
                                 boolean mockSms, String petName) {
}
