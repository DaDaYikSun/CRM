package com.dadayiksun.crm.contact.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/guardians/{id}/contact-panel} (C13, API 설계 3-6).
 * 전체 번호는 이 패널과 보호자 상세에서만 준다 (POL-DATA-002, API 설계 1-4).
 *
 * @param todayContact 오늘 이 보호자에게 남긴 마지막 연락. 없으면 null (POL-CNT-006)
 */
public record ContactPanelResponse(Guardian guardian, TodayContact todayContact, List<Pet> pets) {

    public record Guardian(long guardianId, String name, String phone, String subPhone, String preferredContact,
                           List<String> tags, String contactTimeNote, boolean smsAgreed, String phoneStatus) {
    }

    /** @param time 한국 시간 'HH:mm' */
    public record TodayContact(String staffName, String time) {
    }

    public record Pet(long petId, String name, List<Item> items) {
    }

    /**
     * @param missed          저장된 누락 판정이 있는지만 본다 (K17, T13)
     * @param nextContactDate 그 주기의 다음 연락 예정일. 이 날 전에 다시 안 받으면 2일차가 아니다 (POL-TGT-006, PR #225 리뷰)
     */
    public record Item(long scheduleId, String serviceType, String vaccineTypeName, LocalDate dueDate,
                       boolean missed, int noAnswerCount, LocalDate nextContactDate) {
    }
}
