package com.dadayiksun.crm.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/dashboard/trends} 출력 (FR-DASH-007 · 008, #185, API 설계 3-8).
 *
 * @param weeks        기간에 걸친 모든 주(월요일 시작). 대상이 없는 주도 넣고 rate = null이라 화면이 선을 끊는다
 * @param distribution 서비스별 주기 수 (막대). 아이가 아니라 주기 단위라 합이 재방문율의 아이 수와 다를 수 있다
 */
public record TrendResponse(LocalDate from, LocalDate to, List<Week> weeks, Distributions distribution) {

    /** 계산식은 지표(FR-DASH-002)와 같다: 재방문율 = 1 − 누락률, 아이마다 결과 하나 */
    public record Week(LocalDate weekStart, MetricsResponse.Ratio vaccineRevisit, MetricsResponse.Ratio groomingRevisit,
                       MetricsResponse.Ratio vaccineMiss) {
    }

    public record Distributions(Buckets vaccine, Buckets grooming) {
    }

    public record Buckets(long early, long late0to7, long late8plus, long missed) {
        public static final Buckets EMPTY = new Buckets(0, 0, 0, 0);
    }
}
