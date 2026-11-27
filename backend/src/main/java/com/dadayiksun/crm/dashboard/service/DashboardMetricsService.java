package com.dadayiksun.crm.dashboard.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.dashboard.domain.MetricsPeriod;
import com.dadayiksun.crm.dashboard.dto.MetricsResponse;
import com.dadayiksun.crm.dashboard.repository.DashboardMetricsQuery;

/**
 * S-02 재방문 지표 · 연락 효과 지표 (FR-DASH-002 · 003, C15, API 설계 3-8, DB 설계 5-4).
 * 세는 일은 SQL이 하고(K16 ①), 여기서는 비율 · 빈 칸을 채운다. 결과 유형 5가지 · 연락 방법 2가지는 0건이어도 칸을 둔다.
 */
@Service
@Transactional(readOnly = true)
public class DashboardMetricsService {

    /** 결과 유형 5가지 (NFR-DATA-003, contact_log.result) */
    static final List<String> RESULTS = List.of("BOOKED", "NO_ANSWER", "REFUSED", "OTHER_HOSPITAL", "WRONG_NUMBER");
    /** 연락 방법 (contact_log.method) */
    static final List<String> METHODS = List.of("PHONE", "SMS");

    private final DashboardMetricsQuery query;
    private final KstClock clock;

    public DashboardMetricsService(DashboardMetricsQuery query, KstClock clock) {
        this.query = query;
        this.clock = clock;
    }

    public MetricsResponse metrics(String fromRaw, String toRaw) {
        MetricsPeriod period = MetricsPeriod.parse(fromRaw, toRaw, clock.today());
        Map<String, DashboardMetricsQuery.RevisitCount> revisit = byKey(query.revisit(period.from(), period.to()),
                DashboardMetricsQuery.RevisitCount::serviceType);
        Map<String, DashboardMetricsQuery.GapAverage> gap = byKey(query.gapAverage(period.from(), period.to()),
                DashboardMetricsQuery.GapAverage::serviceType);

        DashboardMetricsQuery.RevisitCount vaccine = revisit.getOrDefault("VACCINE",
                new DashboardMetricsQuery.RevisitCount("VACCINE", 0, 0));
        DashboardMetricsQuery.RevisitCount grooming = revisit.getOrDefault("GROOMING",
                new DashboardMetricsQuery.RevisitCount("GROOMING", 0, 0));

        return new MetricsResponse(period.from(), period.to(),
                new MetricsResponse.Revisit(revisitRatio(vaccine), revisitRatio(grooming)),
                MetricsResponse.Ratio.of(vaccine.missedPets(), vaccine.pets()),
                new MetricsResponse.GapDays(avg(gap.get("VACCINE")), avg(gap.get("GROOMING"))),
                conversions(RESULTS, query.conversionByResult(period.from(), period.to())),
                conversions(METHODS, query.conversionByMethod(period.from(), period.to())));
    }

    /** 재방문 아이 = 대상 아이 − 누락 아이. 재방문율 + 누락률 = 100% (POL-MISS-003) */
    private static MetricsResponse.Ratio revisitRatio(DashboardMetricsQuery.RevisitCount c) {
        return MetricsResponse.Ratio.of(c.pets() - c.missedPets(), c.pets());
    }

    /** 소수 첫째 자리까지 */
    private static Double avg(DashboardMetricsQuery.GapAverage g) {
        return g == null || g.avgGapDays() == null ? null : Math.round(g.avgGapDays() * 10.0) / 10.0;
    }

    private static Map<String, MetricsResponse.Conversion> conversions(List<String> keys,
                                                                       List<DashboardMetricsQuery.ConversionCount> rows) {
        Map<String, DashboardMetricsQuery.ConversionCount> byKey = byKey(rows, DashboardMetricsQuery.ConversionCount::key);
        Map<String, MetricsResponse.Conversion> out = new LinkedHashMap<>();
        for (String k : keys) {
            DashboardMetricsQuery.ConversionCount c = byKey.get(k);
            out.put(k, c == null ? MetricsResponse.Conversion.of(0, 0) : MetricsResponse.Conversion.of(c.converted(), c.total()));
        }
        return out;
    }

    private static <T> Map<String, T> byKey(List<T> rows, Function<T, String> key) {
        return rows.stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
