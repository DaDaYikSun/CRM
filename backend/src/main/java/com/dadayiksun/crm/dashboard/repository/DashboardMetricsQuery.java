package com.dadayiksun.crm.dashboard.repository;

import java.time.LocalDate;
import java.util.List;

/**
 * S-02 지표용 조회 전용 쿼리 (C15, DB 설계 5-4, K16 ① 지표는 SQL 집계, K17 ② 저장된 결과만 읽음).
 * 다른 도메인 테이블을 읽기만 한다: schedule · vaccine_type(cycle), pet(customer), contact_log(contact).
 */
public interface DashboardMetricsQuery {

    /** 서비스별 재방문 대상 아이 수와 그중 누락 아이 수 (POL-MISS-003 · 004, T15) */
    record RevisitCount(String serviceType, long pets, long missedPets) {
    }

    /** 서비스별 완료 주기의 gap_days 평균 (재방문과 같은 대상, 늦게 완료된 주기 포함) */
    record GapAverage(String serviceType, Double avgGapDays) {
    }

    /** 묶음 키(결과 유형 또는 연락 방법)별 연락 수와 전환 수 */
    record ConversionCount(String key, long total, long converted) {
    }

    List<RevisitCount> revisit(LocalDate from, LocalDate to);

    List<GapAverage> gapAverage(LocalDate from, LocalDate to);

    List<ConversionCount> conversionByResult(LocalDate from, LocalDate to);

    List<ConversionCount> conversionByMethod(LocalDate from, LocalDate to);
}
