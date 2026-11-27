package com.dadayiksun.crm.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 더미 기록을 시간 순서로 늘어놓기 (#147 2단계, 적재기가 cycle을 부르는 순서). */
class DummyTimelineTest {

    static final LocalDate REGISTERED = LocalDate.of(2026, 6, 10);

    static DummyTimeline.Event visit(String date, String createdAt) {
        return DummyTimeline.visit("VACCINE", Map.of("visit_date", date, "created_at", createdAt));
    }

    static DummyTimeline.Event contact(String at) {
        return DummyTimeline.contact(Map.of("service_type", "VACCINE", "contacted_at", at));
    }

    @Test
    void 등록일_전_방문은_먼저_넣는_과거_기록이고_등록일_전_연락은_뺀다() {
        DummyTimeline.Plan plan = DummyTimeline.plan(REGISTERED, List.of(
                visit("2026-06-20", "2026-06-20 10:00:00"),
                visit("2025-06-01", "2026-06-10 09:00:00"),
                contact("2026-05-01 11:00:00")));

        assertThat(plan.beforeRegistration()).extracting(e -> e.row().get("visit_date")).containsExactly("2025-06-01");
        assertThat(plan.afterRegistration()).extracting(e -> e.row().get("visit_date")).containsExactly("2026-06-20");
        assertThat(plan.contactsBeforeRegistration()).isEqualTo(1);
    }

    @Test
    void 등록일_당일_기록은_등록_뒤로_본다() {
        DummyTimeline.Plan plan = DummyTimeline.plan(REGISTERED, List.of(visit("2026-06-10", "2026-06-10 15:00:00")));
        assertThat(plan.beforeRegistration()).isEmpty();
        assertThat(plan.afterRegistration()).hasSize(1);
    }

    @Test
    void 방문과_연락을_시각_순서로_섞고_같은_시각이면_방문_먼저() {
        DummyTimeline.Plan plan = DummyTimeline.plan(REGISTERED, List.of(
                contact("2026-07-02 14:00:00"),
                visit("2026-07-02", "2026-07-02 14:00:00"),
                contact("2026-07-01 09:30:00"),
                visit("2026-07-03", "2026-07-03 08:00:00")));

        assertThat(plan.afterRegistration()).extracting(DummyTimeline.Event::kind).containsExactly(
                DummyTimeline.Kind.CONTACT, DummyTimeline.Kind.VISIT, DummyTimeline.Kind.CONTACT, DummyTimeline.Kind.VISIT);
    }

    @Test
    void 방문_시각은_방문일에_입력_시각의_시분을_붙인다() {
        // 늦게 입력한 방문도 방문일 기준으로 줄을 선다
        DummyTimeline.Event late = visit("2026-07-01", "2026-07-05 16:20:00");
        assertThat(late.at()).isEqualTo(LocalDate.of(2026, 7, 1).atTime(16, 20));
    }
}
