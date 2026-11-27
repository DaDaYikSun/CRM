package com.dadayiksun.crm.dashboard.repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDashboardTrendQuery implements DashboardTrendQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcDashboardTrendQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<WeeklyRevisit> weeklyRevisit(LocalDate from, LocalDate to) {
        // 주마다 아이마다 결과 하나: 그 주 대상 주기 중 하나라도 누락 판정이 있으면 누락 아이 (T15, #157 지표와 같은 셈)
        return jdbc.query("""
                SELECT w.week_start, w.service_type, COUNT(*) AS pets, SUM(w.missed) AS missed_pets
                  FROM (SELECT DATE_SUB(s.due_date, INTERVAL WEEKDAY(s.due_date) DAY) AS week_start,
                               s.pet_id, s.service_type, MAX(s.missed_at IS NOT NULL) AS missed
                """ + JdbcDashboardMetricsQuery.TARGET_CYCLES + """
                         GROUP BY week_start, s.pet_id, s.service_type) w
                 GROUP BY w.week_start, w.service_type
                 ORDER BY w.week_start
                """, dates(from, to), (rs, i) -> new WeeklyRevisit(rs.getDate("week_start").toLocalDate(),
                rs.getString("service_type"), rs.getLong("pets"), rs.getLong("missed_pets")));
    }

    @Override
    public List<Distribution> distribution(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT s.service_type,
                       SUM(s.missed_at IS NULL AND s.gap_days < 0) AS early,
                       SUM(s.missed_at IS NULL AND s.gap_days BETWEEN 0 AND 7) AS late0to7,
                       SUM(s.missed_at IS NULL AND s.gap_days >= 8) AS late8plus,
                       SUM(s.missed_at IS NOT NULL) AS missed
                """ + JdbcDashboardMetricsQuery.TARGET_CYCLES + """
                 GROUP BY s.service_type
                """, dates(from, to), (rs, i) -> new Distribution(rs.getString("service_type"), rs.getLong("early"),
                rs.getLong("late0to7"), rs.getLong("late8plus"), rs.getLong("missed")));
    }

    private static Map<String, Object> dates(LocalDate from, LocalDate to) {
        return Map.of("from", Date.valueOf(from), "to", Date.valueOf(to));
    }
}
