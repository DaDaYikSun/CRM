package com.dadayiksun.crm.contact.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.common.setting.SettingService;
import com.dadayiksun.crm.contact.ContactFakes;
import com.dadayiksun.crm.contact.repository.ContactLogRepository.MockSmsRow;
import com.dadayiksun.crm.contact.repository.ContactQuery.GroupItem;
import com.dadayiksun.crm.customer.service.GuardianPhoneStatusService;
import com.dadayiksun.crm.cycle.service.ScheduleContactUpdater;

/** 결과별 후속 처리 (FR-CNT-003, POL-TGT-006, T4, K21). 보호자 7, 아이 12 '보리' */
class ContactFollowUpServiceTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 25, 10, 0);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 26);

    private ContactFakes.Query query;
    private ContactFakes.Logs logs;
    private ScheduleContactUpdater schedules;
    private GuardianPhoneStatusService guardians;
    private ContactFollowUpService service;

    @BeforeEach
    void setUp() {
        query = new ContactFakes.Query();
        logs = new ContactFakes.Logs();
        schedules = mock(ScheduleContactUpdater.class);
        guardians = mock(GuardianPhoneStatusService.class);
        SettingService settings = mock(SettingService.class);
        when(settings.getString("HOSPITAL_NAME")).thenReturn("다다익선 동물병원");
        when(settings.getString("HOSPITAL_PHONE")).thenReturn("0200000000");
        query.templates.put("VACCINE", "[{병원 이름}] {아이 이름}의 {접종 종류} 접종 예정일은 {예정일}입니다. 방문 전 {병원 전화번호}로 예약해 주세요.");
        query.templates.put("GROOMING", "[{병원 이름}] {아이 이름}의 다음 미용 예정일은 {예정일} 무렵입니다. 원하시는 날짜를 {병원 전화번호}로 알려 주세요.");
        service = new ContactFollowUpService(query, schedules, guardians, new MockSmsWriter(query, logs, settings));
    }

    private static GroupItem item(long scheduleId, String result, int prevNoAnswer, String service, String vaccine,
                                  LocalDate due, boolean smsAgreed) {
        // 이전 '전화 안 받음'은 어제 남겨서 다음 연락 예정일이 오늘(2일차)인 상태
        return item(scheduleId, result, prevNoAnswer, prevNoAnswer > 0 ? "NO_ANSWER" : "NONE",
                prevNoAnswer > 0 ? AT.toLocalDate() : null, service, vaccine, due, smsAgreed);
    }

    private static GroupItem item(long scheduleId, String result, int prevNoAnswer, String prevState,
                                  LocalDate prevNext, String service, String vaccine, LocalDate due, boolean smsAgreed) {
        return new GroupItem(scheduleId, 12, "보리", 7, "01000000000", smsAgreed, result,
                "NO_ANSWER".equals(result) ? TOMORROW : null, prevNoAnswer, prevState, prevNext, service, vaccine, vaccine != null, due,
                AT, 3);
    }

    @Test
    void 전화_안_받음_1일차는_다음_날_다시_대상에_오른다() {
        query.groupItems.add(item(101, "NO_ANSWER", 0, "VACCINE", "종합백신", LocalDate.of(2026, 11, 20), true));

        ContactFollowUpService.Outcome out = service.apply("g1");

        verify(schedules).update(101, "NO_ANSWER", 1, TOMORROW);
        assertThat(out.closedScheduleIds()).isEmpty();
        assertThat(out.pendingSms()).isNull();
        assertThat(logs.mockSms).isEmpty();
    }

    @Test
    void 이틀째도_안_받으면_주기를_끝내고_접종_문구로_모의_발송_기록_하나() {
        // 접종 · 미용이 섞이면 접종 문구 (T4). 문자는 한 통
        query.groupItems.add(item(103, "NO_ANSWER", 1, "GROOMING", null, LocalDate.of(2026, 11, 18), true));
        query.groupItems.add(item(101, "NO_ANSWER", 1, "VACCINE", "종합백신", LocalDate.of(2026, 11, 20), true));

        ContactFollowUpService.Outcome out = service.apply("g1");

        verify(schedules).update(103, "CLOSED", 2, null);
        verify(schedules).update(101, "CLOSED", 2, null);
        assertThat(out.closedScheduleIds()).containsExactly(103L, 101L);
        assertThat(logs.mockSms).containsExactly(new MockSmsRow("g1", 7, 12, 101, "VACCINE",
                "[다다익선 동물병원] 보리의 종합백신 접종 예정일은 2026-11-20입니다. 방문 전 0200000000로 예약해 주세요.",
                AT, 3));
        assertThat(out.pendingSms().template()).isEqualTo("VACCINE");
        assertThat(out.pendingSms().phone()).isEqualTo("01000000000");
    }

    @Test
    void 미용만이면_미용_문구() {
        query.groupItems.add(item(103, "NO_ANSWER", 1, "GROOMING", null, LocalDate.of(2026, 11, 18), true));

        service.apply("g1");

        assertThat(logs.mockSms.get(0).body())
                .isEqualTo("[다다익선 동물병원] 보리의 다음 미용 예정일은 2026-11-18 무렵입니다. 원하시는 날짜를 0200000000로 알려 주세요.");
    }

    @Test
    void 같은_날_다시_안_받으면_1일차_그대로_주기를_끝내지_않는다() {
        // 오전에 안 받아 다음 연락 예정일이 내일인데, 오후에 다시 걸어도 안 받음
        query.groupItems.add(item(101, "NO_ANSWER", 1, "NO_ANSWER", TOMORROW, "VACCINE", "종합백신",
                LocalDate.of(2026, 11, 20), true));

        ContactFollowUpService.Outcome out = service.apply("g1");

        verify(schedules).update(101, "NO_ANSWER", 1, TOMORROW);
        assertThat(out.closedScheduleIds()).isEmpty();
        assertThat(logs.mockSms).isEmpty();
    }

    @Test
    void 이미_끝난_주기에_다시_안_받음을_남겨도_문자를_또_남기지_않는다() {
        query.groupItems.add(item(101, "NO_ANSWER", 2, "CLOSED", null, "VACCINE", "종합백신",
                LocalDate.of(2026, 11, 20), true));

        ContactFollowUpService.Outcome out = service.apply("g1");

        verify(schedules).update(101, "CLOSED", 2, null);
        assertThat(out.closedScheduleIds()).isEmpty();
        assertThat(logs.mockSms).isEmpty();
    }

    @Test
    void 수신_동의가_없으면_문자_없이_주기를_끝낸다() {
        query.groupItems.add(item(101, "NO_ANSWER", 1, "VACCINE", "종합백신", LocalDate.of(2026, 11, 20), false));

        ContactFollowUpService.Outcome out = service.apply("g1");

        verify(schedules).update(101, "CLOSED", 2, null);
        assertThat(out.closedScheduleIds()).containsExactly(101L);
        assertThat(out.pendingSms()).isNull();
        assertThat(logs.mockSms).isEmpty();
    }

    @Test
    void 번호_오류면_보호자_번호_상태를_한_번만_WRONG() {
        query.groupItems.add(item(101, "WRONG_NUMBER", 0, "VACCINE", "종합백신", LocalDate.of(2026, 11, 20), true));
        query.groupItems.add(item(103, "WRONG_NUMBER", 0, "GROOMING", null, LocalDate.of(2026, 11, 18), true));

        service.apply("g1");

        verify(guardians, times(1)).markWrong(7);
        verify(schedules).update(101, "WRONG_NUMBER", 0, null);
    }

    @Test
    void 예약함은_그_상태로_두고_번호는_건드리지_않는다() {
        query.groupItems.add(item(101, "BOOKED", 1, "VACCINE", "종합백신", LocalDate.of(2026, 11, 20), true));

        service.apply("g1");

        verify(schedules).update(101, "BOOKED", 0, null);
        verify(guardians, never()).markWrong(anyLong());
    }
}
