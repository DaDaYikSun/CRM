package com.dadayiksun.crm.contact.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.contact.ContactFakes;
import com.dadayiksun.crm.contact.dto.ContactHistoryItem;
import com.dadayiksun.crm.contact.dto.ContactPanelResponse;
import com.dadayiksun.crm.contact.repository.ContactQuery.HistoryRow;
import com.dadayiksun.crm.contact.repository.ContactQuery.PanelItem;
import com.dadayiksun.crm.contact.repository.ContactQuery.PetContactRow;

/** 연락 패널 · 지난 연락 (FR-CNT-001, POL-CNT-006, API 설계 3-6). 2026-11-25(수) 10시 기준. */
class ContactPanelServiceTest {

    private static final long ME = 3L;

    private ContactFakes.Query query;
    private ContactPanelService service;

    @BeforeEach
    void setUp() {
        query = new ContactFakes.Query();
        service = new ContactPanelService(query, ContactFakes.clock());
        query.guardian = new ContactPanelResponse.Guardian(7, "김보호", "01000000000", null, "PHONE",
                List.of("CONTACT_TIME"), "오후 2시 이후", true, "OK");
    }

    private static PanelItem item(long petId, String petName, long scheduleId, int noAnswer) {
        return new PanelItem(petId, petName, new ContactPanelResponse.Item(scheduleId, "VACCINE", "종합백신",
                LocalDate.of(2026, 11, 24), false, noAnswer));
    }

    private static HistoryRow history(long id, LocalDateTime at, String method, String result, String talkedWith,
                                      long createdBy, boolean mockSms) {
        return new HistoryRow(id, at, method, result, talkedWith, null, "데스크", createdBy, false, false, mockSms, "보리");
    }

    @Test
    void 같은_집_아이별로_예정_항목을_묶고_이번_주_일요일까지로_묻는다() {
        query.panelItems.addAll(List.of(item(12, "보리", 101, 1), item(12, "보리", 102, 0), item(13, "콩이", 103, 0)));
        query.todayContact = new ContactPanelResponse.TodayContact("데스크", "09:40");

        ContactPanelResponse panel = service.panel(7);

        assertThat(panel.pets()).extracting(ContactPanelResponse.Pet::name).containsExactly("보리", "콩이");
        assertThat(panel.pets().get(0).items()).extracting(ContactPanelResponse.Item::scheduleId).containsExactly(101L, 102L);
        assertThat(panel.pets().get(0).items().get(0).noAnswerCount()).isEqualTo(1);
        assertThat(panel.todayContact().time()).isEqualTo("09:40");
        assertThat(query.askedWeekEnd).isEqualTo(LocalDate.of(2026, 11, 29));
    }

    @Test
    void 없는_보호자는_404() {
        assertThatThrownBy(() -> service.panel(99))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void 지난_연락은_타임라인_줄_모양이고_내가_오늘_남긴_것만_editable() {
        query.history.addAll(List.of(
                history(2, LocalDateTime.of(2026, 11, 25, 9, 40), "PHONE", "NO_ANSWER", null, ME, false),
                history(1, LocalDateTime.of(2026, 11, 24, 15, 0), "PHONE", "BOOKED", "FAMILY", ME, false)));

        PageResponse<ContactHistoryItem> page = service.history(7, PageQuery.parse("1"), ME);

        ContactHistoryItem today = page.items().get(0);
        assertThat(today.kind()).isEqualTo("CONTACT");
        assertThat(today.summary()).isEqualTo("전화 · 전화 안 받음");
        assertThat(today.editable()).isTrue();
        assertThat(today.recordedAt().getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
        assertThat(page.items().get(1).summary()).isEqualTo("전화 · 가족 · 예약함");
        assertThat(page.items().get(1).editable()).isFalse();
        assertThat(service.history(7, PageQuery.parse("1"), 99L).items().get(0).editable()).isFalse();
    }

    @Test
    void 모의_발송만_한_행은_결과_대신_모의_발송() {
        query.history.add(history(1, LocalDateTime.of(2026, 11, 24, 15, 0), "SMS", null, null, ME, true));

        assertThat(service.history(7, PageQuery.parse("1"), ME).items().get(0).summary()).isEqualTo("문자 · 모의 발송");
    }

    @Test
    void 아이_타임라인_연락_줄은_항목_이름을_앞에_붙인다() {
        query.petHistory.addAll(List.of(
                new PetContactRow(history(2, LocalDateTime.of(2026, 11, 25, 9, 40), "PHONE", "BOOKED", "SELF", ME, false),
                        "VACCINE", "종합백신"),
                new PetContactRow(history(1, LocalDateTime.of(2026, 11, 25, 9, 40), "PHONE", "BOOKED", "SELF", ME, false),
                        "GROOMING", null)));

        assertThat(service.petHistory(12)).extracting(ContactHistoryItem::summary)
                .containsExactly("종합백신 · 전화 · 본인 · 예약함", "미용 · 전화 · 본인 · 예약함");
        assertThat(service.petHistory(12)).extracting(ContactHistoryItem::date)
                .containsOnly(java.time.LocalDate.of(2026, 11, 25));
    }

    @Test
    void 마지막보다_큰_페이지는_빈_목록과_total() {
        query.history.add(history(1, LocalDateTime.of(2026, 11, 24, 15, 0), "PHONE", "BOOKED", "SELF", ME, false));

        PageResponse<ContactHistoryItem> page = service.history(7, PageQuery.parse("2"), ME);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(1);
    }
}
