package com.dadayiksun.crm.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.dashboard.dto.MetricsResponse;
import com.dadayiksun.crm.dashboard.repository.DashboardMetricsQuery;

/** 지표 조립: 비율 · 분자 · 분모, 빈 칸 (FR-DASH-002 · 003, API 설계 3-8, POL-MISS-003 · 004, POL-ERR-008). */
class DashboardMetricsServiceTest {

    static class FakeQuery implements DashboardMetricsQuery {
        List<RevisitCount> revisit = new ArrayList<>();
        List<GapAverage> gap = new ArrayList<>();
        List<ConversionCount> byResult = new ArrayList<>();
        List<ConversionCount> byMethod = new ArrayList<>();
        LocalDate from;
        LocalDate to;

        @Override
        public List<RevisitCount> revisit(LocalDate f, LocalDate t) {
            from = f;
            to = t;
            return revisit;
        }

        @Override
        public List<GapAverage> gapAverage(LocalDate f, LocalDate t) {
            return gap;
        }

        @Override
        public List<ConversionCount> conversionByResult(LocalDate f, LocalDate t) {
            return byResult;
        }

        @Override
        public List<ConversionCount> conversionByMethod(LocalDate f, LocalDate t) {
            return byMethod;
        }
    }

    final FakeQuery query = new FakeQuery();
    final DashboardMetricsService service = new DashboardMetricsService(query,
            new KstClock(Clock.fixed(ZonedDateTime.of(2026, 11, 27, 10, 0, 0, 0, KstClock.ZONE).toInstant(), KstClock.ZONE)));

    @Test
    void 재방문율과_누락률은_아이_기준이고_더하면_100퍼센트() {
        // 접종 대상 아이 50 중 누락 아이 19 (한 아이에 완료 · 누락이 섞여도 SQL이 아이 하나로 센 결과)
        query.revisit.add(new DashboardMetricsQuery.RevisitCount("VACCINE", 50, 19));
        query.revisit.add(new DashboardMetricsQuery.RevisitCount("GROOMING", 31, 9));

        MetricsResponse m = service.metrics(null, null);

        assertThat(m.revisitRate().vaccine()).isEqualTo(new MetricsResponse.Ratio(0.62, 31, 50));
        assertThat(m.vaccineMissRate()).isEqualTo(new MetricsResponse.Ratio(0.38, 19, 50));
        assertThat(m.revisitRate().vaccine().rate() + m.vaccineMissRate().rate()).isEqualTo(1.0);
        assertThat(m.revisitRate().grooming()).isEqualTo(new MetricsResponse.Ratio(0.7097, 22, 31)); // 1 − 0.2903
    }

    @Test
    void 따로_반올림하면_100퍼센트가_안_되는_경우도_합이_정확히_1() {
        // 32마리 중 누락 1: 따로 반올림하면 0.9688 + 0.0313 = 1.0001 (PR #241 리뷰)
        query.revisit.add(new DashboardMetricsQuery.RevisitCount("VACCINE", 32, 1));

        MetricsResponse m = service.metrics(null, null);

        assertThat(m.vaccineMissRate()).isEqualTo(new MetricsResponse.Ratio(0.0313, 1, 32));
        assertThat(m.revisitRate().vaccine()).isEqualTo(new MetricsResponse.Ratio(0.9687, 31, 32));
        assertThat(java.math.BigDecimal.valueOf(m.revisitRate().vaccine().rate()).add(java.math.BigDecimal.valueOf(m.vaccineMissRate().rate())))
                .isEqualByComparingTo("1");
    }

    @Test
    void 반올림_경계에서도_정수로_계산해_틀리지_않는다() {
        // 2000마리 중 누락 1001 = 0.5005 그대로 (소수 계산이면 0.50049999…로 아래로 틀릴 수 있다, PR #241 리뷰)
        query.revisit.add(new DashboardMetricsQuery.RevisitCount("VACCINE", 2000, 1001));
        query.revisit.add(new DashboardMetricsQuery.RevisitCount("GROOMING", 80, 1));

        MetricsResponse m = service.metrics(null, null);

        assertThat(m.vaccineMissRate().rate()).isEqualTo(0.5005);
        assertThat(m.revisitRate().vaccine().rate()).isEqualTo(0.4995);
        assertThat(m.revisitRate().grooming().rate()).isEqualTo(0.9875);
    }

    @Test
    void 기간을_보내지_않으면_이번_달_오늘까지_센다() {
        MetricsResponse m = service.metrics(null, null);
        assertThat(m.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(m.to()).isEqualTo(LocalDate.of(2026, 11, 27)); // 오늘까지 (#176)
        assertThat(query.from).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void 대상이_없으면_rate는_null이고_결과_유형_방법_칸은_모두_있다() {
        MetricsResponse m = service.metrics("2026-06-01", "2026-06-30");

        assertThat(m.revisitRate().vaccine()).isEqualTo(new MetricsResponse.Ratio(null, 0, 0));
        assertThat(m.vaccineMissRate().rate()).isNull();
        assertThat(m.avgGapDays()).isEqualTo(new MetricsResponse.GapDays(null, null));
        assertThat(m.conversionByResult()).containsOnlyKeys("BOOKED", "NO_ANSWER", "REFUSED", "OTHER_HOSPITAL", "WRONG_NUMBER");
        assertThat(m.conversionByResult().get("REFUSED")).isEqualTo(new MetricsResponse.Conversion(null, 0, 0));
        assertThat(m.conversionByMethod()).containsOnlyKeys("PHONE", "SMS");
    }

    @Test
    void 전환율과_방문_차이는_서비스별() {
        query.gap.add(new DashboardMetricsQuery.GapAverage("VACCINE", 6.4444));
        query.gap.add(new DashboardMetricsQuery.GapAverage("GROOMING", -1.25));
        query.byResult.add(new DashboardMetricsQuery.ConversionCount("BOOKED", 20, 16));
        query.byMethod.add(new DashboardMetricsQuery.ConversionCount("SMS", 10, 3));

        MetricsResponse m = service.metrics(null, null);

        assertThat(m.avgGapDays()).isEqualTo(new MetricsResponse.GapDays(6.4, -1.2));
        assertThat(m.conversionByResult().get("BOOKED")).isEqualTo(new MetricsResponse.Conversion(0.8, 16, 20));
        assertThat(m.conversionByMethod().get("SMS")).isEqualTo(new MetricsResponse.Conversion(0.3, 3, 10));
        assertThat(m.conversionByMethod().get("PHONE").rate()).isNull();
    }
}
