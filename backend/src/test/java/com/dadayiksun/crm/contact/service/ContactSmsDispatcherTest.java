package com.dadayiksun.crm.contact.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.contact.dto.SaveContactResponse;
import com.dadayiksun.crm.contact.dto.SendSmsResponse;

/** 커밋 뒤 2일차 문자 (K21): SmsSender가 실패해도 연락 결과는 그대로고 응답의 sent = false로 알린다 */
class ContactSmsDispatcherTest {

    private static ContactSaveService.Saved saved(MockSmsWriter.PendingSms pending) {
        return new ContactSaveService.Saved("g1", new ContactFollowUpService.Outcome(List.of(101L), pending));
    }

    private static final MockSmsWriter.PendingSms PENDING =
            new MockSmsWriter.PendingSms("01012345678", "문구", "VACCINE");

    @Test
    void 보내면_sent_true와_문구_종류() {
        List<String> sent = new ArrayList<>();
        SaveContactResponse res = new ContactSmsDispatcher((phone, body) -> sent.add(phone)).sendAfterCommit(saved(PENDING));

        assertThat(sent).containsExactly("01012345678");
        assertThat(res.followUp()).isEqualTo(new SaveContactResponse.FollowUp(List.of(101L),
                new SaveContactResponse.MockSms(true, "VACCINE")));
    }

    @Test
    void 실패하면_예외_없이_sent_false() {
        SaveContactResponse res = new ContactSmsDispatcher((phone, body) -> {
            throw new IllegalStateException("발송 실패");
        }).sendAfterCommit(saved(PENDING));

        assertThat(res.contactGroupId()).isEqualTo("g1");
        assertThat(res.followUp().mockSms()).isEqualTo(new SaveContactResponse.MockSms(false, "VACCINE"));
    }

    @Test
    void 문자_보내기는_문구와_sent를_주고_실패해도_예외_없이_sent_false() {
        ContactSmsService.Recorded recorded = new ContactSmsService.Recorded("g2", PENDING);

        assertThat(new ContactSmsDispatcher((phone, body) -> { }).sendAfterCommit(recorded))
                .isEqualTo(new SendSmsResponse("g2", "문구", "VACCINE", true));
        assertThat(new ContactSmsDispatcher((phone, body) -> {
            throw new IllegalStateException("발송 실패");
        }).sendAfterCommit(recorded)).isEqualTo(new SendSmsResponse("g2", "문구", "VACCINE", false));
    }

    @Test
    void 보낼_문자가_없으면_부르지_않고_mockSms는_null() {
        List<String> sent = new ArrayList<>();
        SaveContactResponse res = new ContactSmsDispatcher((phone, body) -> sent.add(phone)).sendAfterCommit(saved(null));

        assertThat(sent).isEmpty();
        assertThat(res.followUp().mockSms()).isNull();
        assertThat(res.followUp().closedScheduleIds()).containsExactly(101L);
    }
}
