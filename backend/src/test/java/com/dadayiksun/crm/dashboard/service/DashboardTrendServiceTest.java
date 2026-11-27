package com.dadayiksun.crm.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.dashboard.dto.TrendResponse;
import com.dadayiksun.crm.dashboard.repository.DashboardTrendQuery;

/** 재방문율 추이 · 방문 분포 (FR-DASH-007 · 008, #185). SQL이 센 값을 주 · 칸에 맞춰 넣는 것만 본다. */
class DashboardTrendServiceTest {

    private static final KstClock CLOCK =
            new KstClock(Clock.fixed(ZonedDateTime.of(2026, 11, 27, 10, 0, 0, 0, KstClock.ZONE).toInstant(), KstClock.ZONE));

    private static DashboardTrendService service(List<DashboardTrendQuery.WeeklyRevisit> weekly,
                                                 List<DashboardTrendQuery.Distribution> distribution) {
        return new DashboardTrendService(new DashboardTrendQuery() {
            @Override
            public List<WeeklyRevisit> weeklyRevisit(LocalDate from, LocalDate to) {
                return weekly;
            }

            @Override
            public List<Distribution> distribution(LocalDate from, LocalDate to) {
                return distribution;
            }
        }, CLOCK);
    }

    @Test
    void 기간에_걸친_모든_주를_월요일로_주고_대상이_없는_주는_rate_null() {
        TrendResponse r = service(List.of(), List.of()).trends("2026-11-01", "2026-11-27");

        // 2026-11-01(일)은 10-26(월) 주, 11-27(금)은 11-23 주 → 5주
        assertThat(r.weeks()).extracting(TrendResponse.Week::weekStart).containsExactly(
                LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23));
        assertThat(r.weeks().get(0).vaccineRevisit().rate()).isNull();
        assertThat(r.distribution().vaccine()).isEqualTo(TrendResponse.Buckets.EMPTY);
    }

    @Test
    void 주마다_재방문율은_1_빼기_누락률이고_접종_누락률도_준다() {
        LocalDate week = LocalDate.of(2026, 11, 9);
        TrendResponse r = service(List.of(
                new DashboardTrendQuery.WeeklyRevisit(week, "VACCINE", 4, 1),
                new DashboardTrendQuery.WeeklyRevisit(week, "GROOMING", 2, 0)), List.of())
                .trends("2026-11-01", "2026-11-27");

        TrendResponse.Week w = r.weeks().get(2);
        assertThat(w.weekStart()).isEqualTo(week);
        assertThat(w.vaccineMiss().rate()).isEqualTo(0.25);
        assertThat(w.vaccineRevisit().rate()).isEqualTo(0.75);
        assertThat(w.groomingRevisit().rate()).isEqualTo(1.0);
        assertThat(r.weeks().get(1).groomingRevisit().rate()).isNull(); // 대상 없는 주는 선을 끊는다
    }

    @Test
    void 분포는_서비스별_네_칸() {
        TrendResponse r = service(List.of(), List.of(
                new DashboardTrendQuery.Distribution("VACCINE", 3, 10, 2, 4),
                new DashboardTrendQuery.Distribution("GROOMING", 1, 5, 0, 2)))
                .trends(null, null);

        assertThat(r.distribution().vaccine()).isEqualTo(new TrendResponse.Buckets(3, 10, 2, 4));
        assertThat(r.distribution().grooming()).isEqualTo(new TrendResponse.Buckets(1, 5, 0, 2));
    }
}
