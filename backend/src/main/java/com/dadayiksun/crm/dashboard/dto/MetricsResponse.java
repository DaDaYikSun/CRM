package com.dadayiksun.crm.dashboard.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * C15 지표 (API 설계 3-8). 비율마다 분자 · 분모를 함께 준다. 분모가 0이면 rate = null (POL-ERR-008).
 * 미용 누락 비율 칸은 두지 않는다 (100% − 미용 재방문율, POL-MISS-003).
 */
public record MetricsResponse(
        LocalDate from,
        LocalDate to,
        Revisit revisitRate,
        Ratio vaccineMissRate,
        GapDays avgGapDays,
        Map<String, Conversion> conversionByResult,
        Map<String, Conversion> conversionByMethod) {

    public record Revisit(Ratio vaccine, Ratio grooming) {
    }

    public record Ratio(Double rate, long numerator, long denominator) {
        public static Ratio of(long numerator, long denominator) {
            return new Ratio(ratio(numerator, denominator), numerator, denominator);
        }

        /**
         * 같은 대상의 나머지 비율 (재방문율 = 1 − 누락률, POL-MISS-003). 따로 반올림하면 합이 1이 아닐 수 있어서
         * 반올림한 상대 비율에서 뺀다 (PR #241 리뷰). 분모가 0이면 null
         */
        public Ratio complement() {
            long n = denominator - numerator;
            return new Ratio(rate == null ? null : Math.round((1 - rate) * 10000.0) / 10000.0, n, denominator);
        }
    }

    /** 예정일 대비 실제 방문 차이(일)의 평균. 서비스별로 나눈다 (#157 결정). 완료 주기가 없으면 null */
    public record GapDays(Double vaccine, Double grooming) {
    }

    public record Conversion(Double rate, long converted, long total) {
        public static Conversion of(long converted, long total) {
            return new Conversion(ratio(converted, total), converted, total);
        }
    }

    /** 소수 넷째 자리까지. 분모가 0이면 null */
    static Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : Math.round(numerator * 10000.0 / denominator) / 10000.0;
    }
}
