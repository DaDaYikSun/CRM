package com.dadayiksun.crm.dashboard.repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 재방문율 추이 · 예정일 대비 실제 방문 분포 (FR-DASH-007 · 008, #185). 대상 주기는 #157 지표와 같다
 * ({@link JdbcDashboardMetricsQuery#TARGET_CYCLES}). 세는 일은 SQL이 한다 (K16 ①). 저장된 판정만 읽는다 (K17).
 */
public interface DashboardTrendQuery {

    /** 예정일이 든 주(월요일 시작, 한국 날짜) · 서비스마다 대상 아이 수와 누락 아이 수. 아이마다 결과 하나 (T15) */
    record WeeklyRevisit(LocalDate weekStart, String serviceType, long pets, long missedPets) {
    }

    /**
     * 서비스마다 주기 수. 누락 판정이 저장된 주기는 늦게 완료됐어도 missed에만 센다 (FR-DASH-008).
     * early: 예정일 전, late0to7: 0~7일 늦게(제날 포함), late8plus: 8일 이상 늦게(누락 기준 안)
     */
    record Distribution(String serviceType, long early, long late0to7, long late8plus, long missed) {
    }

    List<WeeklyRevisit> weeklyRevisit(LocalDate from, LocalDate to);

    List<Distribution> distribution(LocalDate from, LocalDate to);
}
