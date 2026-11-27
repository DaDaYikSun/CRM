package com.dadayiksun.crm.dashboard.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.dashboard.domain.MetricsPeriod;
import com.dadayiksun.crm.dashboard.dto.MetricsResponse.Ratio;
import com.dadayiksun.crm.dashboard.dto.TrendResponse;
import com.dadayiksun.crm.dashboard.dto.TrendResponse.Buckets;
import com.dadayiksun.crm.dashboard.repository.DashboardTrendQuery;

/**
 * S-02 재방문율 추이 · 예정일 대비 실제 방문 분포 (FR-DASH-007 · 008, #185).
 * 기간은 지표와 같은 규칙({@link MetricsPeriod}, #176). 추이는 한 주(월~일, 한국 날짜) 단위 (#185 결정). 목표선은 없다 (FR-DASH-007).
 */
@Service
@Transactional(readOnly = true)
public class DashboardTrendService {

    private final DashboardTrendQuery query;
    private final KstClock clock;

    public DashboardTrendService(DashboardTrendQuery query, KstClock clock) {
        this.query = query;
        this.clock = clock;
    }

    public TrendResponse trends(String fromRaw, String toRaw) {
        MetricsPeriod period = MetricsPeriod.parse(fromRaw, toRaw, clock.today());
        Map<String, DashboardTrendQuery.WeeklyRevisit> byWeek = new HashMap<>();
        for (DashboardTrendQuery.WeeklyRevisit w : query.weeklyRevisit(period.from(), period.to())) {
            byWeek.put(w.weekStart() + "/" + w.serviceType(), w);
        }
        List<TrendResponse.Week> weeks = new ArrayList<>();
        for (LocalDate week = monday(period.from()); !week.isAfter(period.to()); week = week.plusWeeks(1)) {
            Ratio vaccineMiss = missRatio(byWeek.get(week + "/VACCINE"));
            Ratio groomingMiss = missRatio(byWeek.get(week + "/GROOMING"));
            weeks.add(new TrendResponse.Week(week, vaccineMiss.complement(), groomingMiss.complement(), vaccineMiss));
        }

        Buckets vaccine = Buckets.EMPTY;
        Buckets grooming = Buckets.EMPTY;
        for (DashboardTrendQuery.Distribution d : query.distribution(period.from(), period.to())) {
            Buckets b = new Buckets(d.early(), d.late0to7(), d.late8plus(), d.missed());
            if ("VACCINE".equals(d.serviceType())) {
                vaccine = b;
            } else if ("GROOMING".equals(d.serviceType())) {
                grooming = b;
            }
        }
        return new TrendResponse(period.from(), period.to(), weeks, new TrendResponse.Distributions(vaccine, grooming));
    }

    static LocalDate monday(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 대상이 없는 주는 0/0이라 rate = null (POL-ERR-008) */
    private static Ratio missRatio(DashboardTrendQuery.WeeklyRevisit w) {
        return w == null ? Ratio.of(0, 0) : Ratio.of(w.missedPets(), w.pets());
    }
}
