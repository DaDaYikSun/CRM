package com.dadayiksun.crm.contact.dto;

import java.util.List;

/**
 * {@code POST /api/contacts/sms} 입력 ([문자 보내기], #174, API 설계 3-6). 검사는 {@code ContactSmsService}가 한다.
 * 문구 종류는 서버가 항목으로 정한다 (접종이 있으면 접종, T4, #174 결정).
 */
public record SendSmsRequest(Long guardianId, List<Item> items) {

    public record Item(Long scheduleId) {
    }
}
