package com.dadayiksun.crm.contact.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.setting.SettingService;
import com.dadayiksun.crm.contact.ContactFakes;
import com.dadayiksun.crm.contact.dto.SendSmsRequest;
import com.dadayiksun.crm.contact.repository.ContactLogRepository.MockSmsRow;
import com.dadayiksun.crm.contact.repository.ContactQuery.SmsTarget;

/** [문자 보내기] 모의 발송 (FR-CNT-005, POL-CNT-005, POL-REG-010, #174). 보호자 7, 직원 3 */
class ContactSmsServiceTest {

    private ContactFakes.Query query;
    private ContactFakes.Logs logs;
    private ContactSmsService service;

    @BeforeEach
    void setUp() {
        query = new ContactFakes.Query();
        logs = new ContactFakes.Logs();
        SettingService settings = mock(SettingService.class);
        when(settings.getString("HOSPITAL_NAME")).thenReturn("다다익선 동물병원");
        when(settings.getString("HOSPITAL_PHONE")).thenReturn("0200000000");
        query.templates.put("VACCINE", "[{병원 이름}] {아이 이름}의 {접종 종류} 접종 예정일은 {예정일}입니다. 방문 전 {병원 전화번호}로 예약해 주세요.");
        query.templates.put("GROOMING", "[{병원 이름}] {아이 이름}의 다음 미용 예정일은 {예정일} 무렵입니다. 원하시는 날짜를 {병원 전화번호}로 알려 주세요.");
        service = new ContactSmsService(query, new MockSmsWriter(query, logs, settings), ContactFakes.clock());
    }

    private static SmsTarget target(long scheduleId, long petId, String petName, long guardianId, boolean agreed,
                                    boolean active, String service, String vaccine, String due) {
        // 테스트 안에서 주요 접종이 아닌 것은 심장사상충 예방약뿐
        boolean major = vaccine != null && !vaccine.startsWith("심장사상충");
        return new SmsTarget(scheduleId, petId, petName, guardianId, "01000000000", agreed, active, service, vaccine,
                major, LocalDate.parse(due));
    }

    private static SendSmsRequest req(Long guardianId, Long... scheduleIds) {
        return new SendSmsRequest(guardianId, java.util.Arrays.stream(scheduleIds).map(SendSmsRequest.Item::new).toList());
    }

    private static ErrorCode code(Throwable e) {
        return ((ApiException) e).code();
    }

    @Test
    void 동의한_보호자는_한_통_한_행으로_기록하고_접종이_있으면_접종_문구() {
        query.smsTargets.add(target(103, 13, "콩이", 7, true, true, "GROOMING", null, "2026-11-18"));
        query.smsTargets.add(target(101, 12, "보리", 7, true, true, "VACCINE", "종합백신", "2026-11-20"));

        ContactSmsService.Recorded out = service.record(req(7L, 103L, 101L), 3);

        assertThat(logs.mockSms).containsExactly(new MockSmsRow(out.contactGroupId(), 7, 12, 101, "VACCINE",
                "[다다익선 동물병원] 보리의 종합백신 접종 예정일은 2026-11-20입니다. 방문 전 0200000000로 예약해 주세요.",
                LocalDateTime.of(2026, 11, 25, 10, 0), 3));
        assertThat(logs.rows).isEmpty(); // 결과 행은 남기지 않는다 (후속 처리 그대로)
        assertThat(out.sms().template()).isEqualTo("VACCINE");
        assertThat(out.sms().phone()).isEqualTo("01000000000");
    }

    @Test
    void 예정일이_같은_접종이_여럿이면_주요_접종_문구() {
        query.smsTargets.add(target(105, 12, "보리", 7, true, true, "VACCINE", "심장사상충 예방약", "2026-11-20"));
        query.smsTargets.add(target(104, 12, "보리", 7, true, true, "VACCINE", "종합백신", "2026-11-20"));

        ContactSmsService.Recorded out = service.record(req(7L, 105L, 104L), 3);

        assertThat(logs.mockSms.get(0).scheduleId()).isEqualTo(104);
        assertThat(out.sms().body()).contains("보리의 종합백신 접종 예정일은 2026-11-20");
    }

    @Test
    void 미용만_있으면_미용_문구() {
        query.smsTargets.add(target(103, 13, "콩이", 7, true, true, "GROOMING", null, "2026-11-18"));

        ContactSmsService.Recorded out = service.record(req(7L, 103L), 3);

        assertThat(out.sms().template()).isEqualTo("GROOMING");
        assertThat(out.sms().body()).isEqualTo(
                "[다다익선 동물병원] 콩이의 다음 미용 예정일은 2026-11-18 무렵입니다. 원하시는 날짜를 0200000000로 알려 주세요.");
    }

    @Test
    void 수신_동의가_없으면_409이고_기록하지_않는다() {
        query.smsTargets.add(target(101, 12, "보리", 7, false, true, "VACCINE", "종합백신", "2026-11-20"));

        assertThatThrownBy(() -> service.record(req(7L, 101L), 3))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.SMS_NOT_AGREED));
        assertThat(logs.mockSms).isEmpty();
    }

    @Test
    void 무지개다리_아이면_409() {
        query.smsTargets.add(target(101, 12, "보리", 7, true, false, "VACCINE", "종합백신", "2026-11-20"));

        assertThatThrownBy(() -> service.record(req(7L, 101L), 3))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.PET_RAINBOW));
    }

    @Test
    void 다른_보호자_예정이나_끝난_예정_빈_항목_중복은_400() {
        query.smsTargets.add(target(101, 12, "보리", 7, true, true, "VACCINE", "종합백신", "2026-11-20"));
        query.smsTargets.add(target(201, 22, "몽이", 8, true, true, "GROOMING", null, "2026-11-20"));

        for (SendSmsRequest bad : List.of(req(7L, 101L, 201L), req(7L, 999L), req(7L), req(null, 101L),
                req(7L, 101L, 101L))) {
            assertThatThrownBy(() -> service.record(bad, 3))
                    .satisfies(e -> assertThat(code(e)).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        assertThat(logs.mockSms).isEmpty();
    }
}
