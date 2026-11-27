package com.dadayiksun.crm.contact.dto;

/**
 * {@code POST /api/contacts/sms} 출력 201 (API 설계 3-6).
 *
 * @param body     기록한 문구 원문 (sms_body)
 * @param template VACCINE · GROOMING
 * @param sent     SmsSender가 보냈는지. false면 기록은 남았고 화면은 정책 4-2 '문자 발송 실패'를 띄운다 (K21)
 */
public record SendSmsResponse(String contactGroupId, String body, String template, boolean sent) {
}
