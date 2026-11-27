package com.dadayiksun.crm.contact.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.dadayiksun.crm.contact.dto.ContactPanelResponse;

/**
 * 연락 패널 · 지난 연락 · 저장 검사에 필요한 값을 읽는 조회 전용 쿼리 (K17 ②).
 * guardian · pet(customer), schedule · vaccine_type(cycle), staff(auth)를 읽기만 하고 판단을 다시 하지 않는다.
 * 누락은 저장된 {@code missed_at}만 본다 (T13).
 */
public interface ContactQuery {

    Optional<ContactPanelResponse.Guardian> guardian(long guardianId);

    /** 무지개다리 아닌 아이의 진행 중(OPEN) 예정 중 이번 주 일요일까지거나 누락인 것. 연락 대상 목록(#152)과 같은 범위 */
    List<PanelItem> panelItems(long guardianId, LocalDate weekEnd);

    /** 오늘(한국 시간) 이 보호자에게 남긴 마지막 연락 (POL-CNT-006) */
    Optional<ContactPanelResponse.TodayContact> todayContact(long guardianId, LocalDate today);

    long countHistory(long guardianId);

    /** 최근 순 */
    List<HistoryRow> history(long guardianId, int limit, long offset);

    /** 한 아이의 연락 기록 전부, 최근 순 (아이 상세 통합 타임라인, FR-VIS-003). 행마다 어떤 예정 항목에 남긴 것인지 붙는다 */
    List<HistoryRow> petHistory(long petId);

    /** 진행 중(OPEN)인 예정만 돌려준다. 없거나 끝난 예정은 빠진다 */
    List<OpenSchedule> openSchedules(Collection<Long> scheduleIds);

    record PanelItem(long petId, String petName, ContactPanelResponse.Item item) {
    }

    /** @param itemName 그 연락을 남긴 예정 항목: 접종이면 접종 종류 이름, 미용이면 '미용' */
    record HistoryRow(long id, LocalDateTime contactedAt, String method, String result, String talkedWith, String memo,
                      String staffName, long createdBy, boolean edited, boolean converted, boolean mockSms,
                      String petName, String itemName) {
    }

    record OpenSchedule(long scheduleId, long petId, long guardianId, boolean petActive) {
    }
}
