package com.dadayiksun.crm.contact.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** 결과별 후속 처리 (FR-CNT-003, POL-TGT-005~009). 오늘 11/25 */
class FollowUpRuleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 26);
    private static final FollowUpRule.Prev FRESH = new FollowUpRule.Prev("NONE", 0, null);

    @Test
    void 전화_안_받음_1일차는_다음_연락_예정일까지_빼고_횟수_1() {
        assertThat(FollowUpRule.after(ContactResult.NO_ANSWER, FRESH, TOMORROW, TODAY))
                .isEqualTo(new FollowUpRule.ScheduleContact("NO_ANSWER", 1, TOMORROW, false));
    }

    @Test
    void 다음_연락_예정일_당일에도_안_받으면_2일차로_주기를_끝낸다() {
        FollowUpRule.Prev yesterday = new FollowUpRule.Prev("NO_ANSWER", 1, TODAY);
        assertThat(FollowUpRule.after(ContactResult.NO_ANSWER, yesterday, TOMORROW, TODAY))
                .isEqualTo(new FollowUpRule.ScheduleContact("CLOSED", 2, null, true));
    }

    @Test
    void 다음_연락_예정일_전에_다시_안_받으면_횟수는_그대로_예정일만_새로() {
        // 오전에 안 받아 예정일이 내일인데 오후에 다시 걸어도 안 받음 → 아직 1일차 (하루에 한 번만 센다)
        FollowUpRule.Prev morning = new FollowUpRule.Prev("NO_ANSWER", 1, TOMORROW);
        assertThat(FollowUpRule.after(ContactResult.NO_ANSWER, morning, TOMORROW, TODAY))
                .isEqualTo(new FollowUpRule.ScheduleContact("NO_ANSWER", 1, TOMORROW, false));
    }

    @Test
    void 이미_끝난_주기는_다시_끝내지_않는다() {
        FollowUpRule.Prev closed = new FollowUpRule.Prev("CLOSED", 2, null);
        assertThat(FollowUpRule.after(ContactResult.NO_ANSWER, closed, TOMORROW, TODAY))
                .isEqualTo(new FollowUpRule.ScheduleContact("CLOSED", 2, null, false));
    }

    @Test
    void 다른_결과는_그_상태로_두고_연속_횟수를_0으로() {
        FollowUpRule.Prev once = new FollowUpRule.Prev("NO_ANSWER", 1, TOMORROW);
        for (ContactResult r : new ContactResult[] {ContactResult.BOOKED, ContactResult.REFUSED,
                ContactResult.OTHER_HOSPITAL, ContactResult.WRONG_NUMBER}) {
            assertThat(FollowUpRule.after(r, once, null, TODAY))
                    .isEqualTo(new FollowUpRule.ScheduleContact(r.name(), 0, null, false));
        }
    }

    @Test
    void 숫자는_정책_고정값() {
        assertThat(FollowUpRule.NO_ANSWER_RETRY_DAYS).isEqualTo(1);
        assertThat(FollowUpRule.NO_ANSWER_MAX_DAYS).isEqualTo(2);
    }
}
