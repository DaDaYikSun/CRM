package com.dadayiksun.crm.target.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 연락 대상 목록 한 줄 = 보호자 한 명 (API 설계 3-6 {@code GET /api/targets}, POL-TGT-011).
 * {@code preferredContact} · {@code tags} · {@code contactTimeNote} · {@code nextContactDate} · {@code processedToday}는
 * FR-TGT-004(#173)가 채운다. 이 단계(#152)에서는 null이다 (API 설계 3-6).
 */
public record TargetLineResponse(
        long guardianId,
        String guardianName,
        String phoneLast4,
        String preferredContact,
        List<String> tags,
        String contactTimeNote,
        LocalDate nextContactDate,
        Object processedToday,
        List<Pet> pets) {

    /** 아이 한 마리. {@code items}: 가장 가까운 접종 하나 + 미용, {@code moreItems}: 펼치면 보일 나머지 접종 (FR-TGT-002) */
    public record Pet(long petId, String name, List<Item> items, List<Item> moreItems) {
    }

    /** 예정 항목 하나. {@code missed}는 저장된 누락 판정이 있는지 (T13) */
    public record Item(long scheduleId, String serviceType, String vaccineTypeName, LocalDate dueDate, boolean missed) {
    }
}
