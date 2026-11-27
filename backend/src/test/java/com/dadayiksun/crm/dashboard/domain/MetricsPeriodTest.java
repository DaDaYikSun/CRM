package com.dadayiksun.crm.dashboard.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;

/** 지표 기간 (API 설계 3-8, 화면 설계 SCR-S02-03 '처음: 기간 이번 달'). */
class MetricsPeriodTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

    @Test
    void 기간을_보내지_않으면_이번_달_오늘까지() {
        assertThat(MetricsPeriod.parse(null, " ", TODAY))
                .isEqualTo(new MetricsPeriod(LocalDate.of(2026, 11, 1), TODAY));
    }

    @Test
    void 보낸_기간을_양_끝_포함으로_쓴다() {
        assertThat(MetricsPeriod.parse("2026-06-01", "2026-11-25", TODAY))
                .isEqualTo(new MetricsPeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 25)));
    }

    @Test
    void 한쪽만_오면_같은_달의_처음이나_끝으로_채운다() {
        assertThat(MetricsPeriod.parse("2026-02-10", null, TODAY).to()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(MetricsPeriod.parse(null, "2026-02-10", TODAY).from()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void 형식이_틀리거나_시작이_끝보다_뒤면_400() {
        assertThatThrownBy(() -> MetricsPeriod.parse("2026-13-01", null, TODAY))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        // 시작일은 오늘 전이어야 미래 날짜 검사가 아니라 뒤집힌 기간 검사를 본다 (PR #255 리뷰)
        assertThatThrownBy(() -> MetricsPeriod.parse("2026-11-20", "2026-11-01", TODAY))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.fields()).extracting("field").containsExactly("from"));
    }

    @Test
    void 끝이_오늘_뒤면_오늘로_자르고_시작이_오늘_뒤면_400() {
        assertThat(MetricsPeriod.parse("2026-09-01", "2026-12-31", TODAY))
                .isEqualTo(new MetricsPeriod(LocalDate.of(2026, 9, 1), TODAY));
        assertThat(MetricsPeriod.parse("2026-11-27", "2026-11-27", TODAY))
                .isEqualTo(new MetricsPeriod(TODAY, TODAY));
        assertThat(MetricsPeriod.parse(null, "2026-12-15", TODAY)) // 끝만 미래면 오늘까지, 시작은 그 달 1일
                .isEqualTo(new MetricsPeriod(LocalDate.of(2026, 11, 1), TODAY));
        assertThatThrownBy(() -> MetricsPeriod.parse("2026-11-28", "2026-12-31", TODAY))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.fields()).extracting("field").containsExactly("from"));
    }
}
