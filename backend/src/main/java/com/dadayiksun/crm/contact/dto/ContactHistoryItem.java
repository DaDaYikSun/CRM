package com.dadayiksun.crm.contact.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code GET /api/guardians/{id}/contacts} 한 줄: 타임라인 {@code CONTACT} 줄 모양 + {@code petName} (API 설계 3-5 · 3-6).
 *
 * @param summary    "전화 · 본인 · 전화 안 받음"
 * @param editable   로그인한 직원이 오늘 남긴 기록인지 (POL-DATA-006). 수정 화면은 초안 28
 * @param method     PHONE · SMS. S-06 표의 열을 나누려고 summary와 따로 준다 (화면 설계 SCR-S06-02, #223)
 * @param talkedWith SELF · FAMILY, 문자면 null
 * @param result     5가지 결과 코드, 모의 발송만 한 행이면 null
 * @param itemName   예정 항목: 접종 종류 이름 또는 '미용'
 */
public record ContactHistoryItem(String kind, long id, LocalDate date, String summary, String memo, String staffName,
                                 OffsetDateTime recordedAt, boolean edited, boolean editable, boolean converted,
                                 boolean mockSms, String petName, String method, String talkedWith, String result,
                                 String itemName) {
}
