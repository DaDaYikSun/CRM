package com.dadayiksun.crm.contact.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** 결과별 후속 처리 (FR-CNT-003, POL-TGT-005~009) */
class FollowUpRuleTest {

    private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 26);

    @Test
    void 전화_안_받음_1일차는_다음_연락_예정일까지_빼고_횟수_1() {
        assertThat(FollowUpRule.after(ContactResult.NO_ANSWER, 0, TOMORROW))
                .isEqualTo(new FollowUpRule.ScheduleContact("NO_ANSWER", 1, TOMORROW, false));
    }

    @Test
    void 전화_안_받음_2일차는_이번_주기_연락을_끝낸다() {
        assertThat(FollowUpRule.after(ContactResult.NO_ANSWER, 1, TOMORROW))
                .isEqualTo(new FollowUpRule.ScheduleContact("CLOSED", 2, null, true));
    }

    @Test
    void 다른_결과는_그_상태로_두고_연속_횟수를_0으로() {
        for (ContactResult r : new ContactResult[] {ContactResult.BOOKED, ContactResult.REFUSED,
                ContactResult.OTHER_HOSPITAL, ContactResult.WRONG_NUMBER}) {
            assertThat(FollowUpRule.after(r, 1, null)).isEqualTo(new FollowUpRule.ScheduleContact(r.name(), 0, null, false));
        }
    }

    @Test
    void 숫자는_정책_고정값() {
        assertThat(FollowUpRule.NO_ANSWER_RETRY_DAYS).isEqualTo(1);
        assertThat(FollowUpRule.NO_ANSWER_MAX_DAYS).isEqualTo(2);
    }
}
