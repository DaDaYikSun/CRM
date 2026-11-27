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
import com.dadayiksun.crm.contact.ContactFakes;
import com.dadayiksun.crm.contact.dto.ContactPanelResponse;
import com.dadayiksun.crm.contact.dto.SaveContactRequest;
import com.dadayiksun.crm.contact.dto.SaveContactResponse;
import com.dadayiksun.crm.contact.repository.ContactLogRepository.Row;
import com.dadayiksun.crm.contact.repository.ContactQuery.OpenSchedule;

/** 연락 결과 저장 (FR-CNT-002, POL-CNT-002 · 003, POL-TGT-006, API 설계 3-6). 2026-11-25 10시 기준. */
class ContactSaveServiceTest {

    private static final long GUARDIAN = 7L;
    private static final long STAFF = 3L;

    private ContactFakes.Query query;
    private ContactFakes.Logs logs;
    private ContactFakes.FollowUp followUp;
    private ContactSaveService service;

    @BeforeEach
    void setUp() {
        query = new ContactFakes.Query();
        logs = new ContactFakes.Logs();
        followUp = new ContactFakes.FollowUp();
        service = new ContactSaveService(query, logs, followUp, ContactFakes.clock());
        // 아이 2마리 · 예정 3개 (보리 접종 2, 콩이 미용 1)
        query.open.addAll(List.of(new OpenSchedule(101, 12, GUARDIAN, true), new OpenSchedule(102, 12, GUARDIAN, true),
                new OpenSchedule(103, 13, GUARDIAN, true)));
    }

    private static SaveContactRequest request(String memo, LocalDate next, SaveContactRequest.Item... items) {
        return new SaveContactRequest(GUARDIAN, "PHONE", "SELF", memo, next, List.of(items), false);
    }

    private static SaveContactRequest.Item item(long scheduleId, String result) {
        return new SaveContactRequest.Item(scheduleId, result);
    }

    @Test
    void 예정_항목마다_한_행_같은_묶음으로_저장하고_직원_시간은_서버가_붙인다() {
        SaveContactResponse res = service.save(request("광견병 같이", null,
                item(101, "BOOKED"), item(102, "BOOKED"), item(103, "REFUSED")), STAFF);

        assertThat(logs.rows).hasSize(3);
        assertThat(logs.rows).extracting(Row::groupId).containsOnly(res.contactGroupId());
        assertThat(logs.rows).extracting(Row::petId).containsExactly(12L, 12L, 13L);
        assertThat(logs.rows).extracting(Row::result).containsExactly("BOOKED", "BOOKED", "REFUSED");
        assertThat(logs.rows).extracting(Row::createdBy).containsOnly(STAFF);
        assertThat(logs.rows).extracting(Row::contactedAt).containsOnly(LocalDateTime.of(2026, 11, 25, 10, 0));
        assertThat(logs.rows).extracting(Row::memo).containsOnly("광견병 같이");
        assertThat(followUp.applied).containsExactly(res.contactGroupId());
        assertThat(res.followUp()).isNull();
    }

    @Test
    void 전화_안_받음인데_다음_연락_예정일이_비면_내일로_채운다() {
        service.save(request(null, null, item(101, "NO_ANSWER"), item(103, "BOOKED")), STAFF);

        assertThat(logs.rows).extracting(Row::nextContactDate).containsExactly(LocalDate.of(2026, 11, 26), null);
    }

    @Test
    void 다음_연락_예정일을_넣었으면_그대로_쓴다() {
        service.save(request(null, LocalDate.of(2026, 11, 30), item(101, "NO_ANSWER")), STAFF);

        assertThat(logs.rows.get(0).nextContactDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    private void guardianSmsAgreed(boolean agreed) {
        query.guardian = new ContactPanelResponse.Guardian(GUARDIAN, "김보호", "01012345678", null, null, List.of(),
                null, agreed, "OK");
    }

    private static SaveContactRequest smsRequest() {
        return new SaveContactRequest(GUARDIAN, "SMS", null, null, null, List.of(item(101, "BOOKED")), false);
    }

    @Test
    void 수신_동의가_없으면_문자_연락은_409이고_저장하지_않는다() {
        guardianSmsAgreed(false);

        assertThatThrownBy(() -> service.save(smsRequest(), STAFF))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.SMS_NOT_AGREED));
        assertThat(logs.rows).isEmpty();
    }

    @Test
    void 수신_동의가_있으면_문자_연락을_저장한다() {
        guardianSmsAgreed(true);

        service.save(smsRequest(), STAFF);

        assertThat(logs.rows).extracting(Row::method).containsExactly("SMS");
    }

    @Test
    void 다음_연락_예정일은_전화_안_받음_행에만_남는다() {
        service.save(request(null, LocalDate.of(2026, 11, 30), item(101, "NO_ANSWER"), item(103, "BOOKED")), STAFF);

        assertThat(logs.rows).extracting(Row::nextContactDate).containsExactly(LocalDate.of(2026, 11, 30), null);
    }

    @Test
    void 다음_연락_예정일이_오늘보다_앞이면_400이고_저장하지_않는다() {
        assertThatThrownBy(() -> service.save(request(null, LocalDate.of(2026, 11, 24), item(101, "NO_ANSWER")), STAFF))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.fields()).extracting("field").containsExactly("nextContactDate"));
        assertThat(logs.rows).isEmpty();
    }

    @Test
    void 정해진_5가지_밖의_결과는_400이고_아무것도_저장하지_않는다() {
        assertThatThrownBy(() -> service.save(request(null, null, item(101, "LATER")), STAFF))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.fields()).extracting("field").containsExactly("items[0].result");
                });
        assertThat(logs.rows).isEmpty();
        assertThat(followUp.applied).isEmpty();
    }

    @Test
    void 결과가_없으면_필수_항목_문구() {
        assertThatThrownBy(() -> service.save(request(null, null, item(101, null)), STAFF))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("연락 결과을(를) 입력해 주세요."));
    }

    @Test
    void 다른_보호자의_예정이나_진행_중이_아닌_예정은_400() {
        query.open.add(new OpenSchedule(201, 30, 99L, true));

        assertThatThrownBy(() -> service.save(request(null, null, item(201, "BOOKED")), STAFF))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> service.save(request(null, null, item(999, "BOOKED")), STAFF))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(logs.rows).isEmpty();
    }

    @Test
    void 같은_예정을_두_번_넣으면_400() {
        assertThatThrownBy(() -> service.save(request(null, null, item(101, "BOOKED"), item(101, "REFUSED")), STAFF))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 무지개다리_아이의_예정이면_409_PET_RAINBOW() {
        query.open.add(new OpenSchedule(104, 14, GUARDIAN, false));

        assertThatThrownBy(() -> service.save(request(null, null, item(104, "BOOKED")), STAFF))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PET_RAINBOW));
        assertThat(logs.rows).isEmpty();
    }

    @Test
    void 메모는_200자까지() {
        service.save(request("가".repeat(200), null, item(101, "BOOKED")), STAFF);
        assertThatThrownBy(() -> service.save(request("가".repeat(201), null, item(101, "BOOKED")), STAFF))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.fields()).extracting("field").contains("memo"));
    }

    @Test
    void 연락_방법_통화한_사람도_정해진_값만() {
        SaveContactRequest bad = new SaveContactRequest(GUARDIAN, "KAKAO", "FRIEND", null, null,
                List.of(item(101, "BOOKED")), false);

        assertThatThrownBy(() -> service.save(bad, STAFF))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.fields()).extracting("field").contains("method", "talkedWith"));
    }
}
