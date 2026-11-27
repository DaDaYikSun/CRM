package com.dadayiksun.crm.contact.dto;

import java.util.List;

/**
 * {@code POST /api/contacts} 출력 201 (API 설계 3-6).
 *
 * @param followUp 결과별 후속 처리 결과 (C14, #154)
 */
public record SaveContactResponse(String contactGroupId, FollowUp followUp) {

    /**
     * @param closedScheduleIds 2일차 '전화 안 받음'으로 이번 주기 연락을 끝낸 예정 항목
     * @param mockSms           2일차 안내 문자. 끝낸 항목이 없거나 수신 동의가 없으면 null (문자 없이 마침)
     */
    public record FollowUp(List<Long> closedScheduleIds, MockSms mockSms) {
    }

    /**
     * @param sent     SmsSender가 보냈는지. false면 화면은 정책 4-2 '문자 발송 실패 (2일차)'를 띄운다 (K21)
     * @param template VACCINE · GROOMING (접종 · 미용이 섞이면 VACCINE, T4)
     */
    public record MockSms(boolean sent, String template) {
    }
}
