package com.dadayiksun.crm.dashboard.repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@link DashboardMetricsQuery}의 MySQL 구현. 날짜 판정을 다시 하지 않는다: 누락은 저장된 missed_at만 본다 (T13 · K17 ②).
 * 무지개다리 아이는 모든 지표에서 뺀다 (POL-MISS-008). 기간은 한국 날짜 양 끝 포함 (DB 연결 시간대 +09:00, DB 설계 1장).
 */
@Repository
public class JdbcDashboardMetricsQuery implements DashboardMetricsQuery {

    /**
     * 재방문 · 누락 대상 주기: 기간 안에 예정일이 된 주기 중 결과가 정해진 것(완료 또는 누락 판정 저장),
     * VOID가 아니고 ACTIVE 아이, 접종은 주요 접종만 (POL-MISS-003 · 004, API 설계 3-8, DB 설계 5-4)
     */
    private static final String TARGET_CYCLES = """
              FROM schedule s
              JOIN pet p ON p.pet_id = s.pet_id AND p.status = 'ACTIVE'
              LEFT JOIN vaccine_type vt ON vt.vaccine_type_id = s.vaccine_type_id
             WHERE s.due_date BETWEEN :from AND :to
               AND s.status <> 'VOID'
               AND (s.status = 'DONE' OR s.missed_at IS NOT NULL)
               AND (s.service_type = 'GROOMING' OR vt.is_major = 1)
            """;

    /** 기간 안에 연락한 연락 (연락한 날 기준, #157 결정), ACTIVE 아이만 */
    private static final String CONTACTS_IN_PERIOD = """
              FROM contact_log c
              JOIN pet p ON p.pet_id = c.pet_id AND p.status = 'ACTIVE'
             WHERE c.contacted_at >= :fromStart AND c.contacted_at < :toEnd
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcDashboardMetricsQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RevisitCount> revisit(LocalDate from, LocalDate to) {
        // 아이마다 결과 하나: 대상 주기 중 하나라도 누락 판정이 있으면 누락 아이 (T15, #116)
        return jdbc.query("""
                SELECT per_pet.service_type, COUNT(*) AS pets, SUM(per_pet.missed) AS missed_pets
                  FROM (SELECT s.pet_id, s.service_type, MAX(s.missed_at IS NOT NULL) AS missed
                """ + TARGET_CYCLES + """
                         GROUP BY s.pet_id, s.service_type) per_pet
                 GROUP BY per_pet.service_type
                """, dates(from, to), (rs, i) -> new RevisitCount(rs.getString("service_type"),
                rs.getLong("pets"), rs.getLong("missed_pets")));
    }

    @Override
    public List<GapAverage> gapAverage(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT s.service_type, AVG(s.gap_days) AS avg_gap_days
                """ + TARGET_CYCLES + """
                   AND s.status = 'DONE'
                 GROUP BY s.service_type
                """, dates(from, to), (rs, i) -> new GapAverage(rs.getString("service_type"),
                rs.getObject("avg_gap_days") == null ? null : rs.getDouble("avg_gap_days")));
    }

    @Override
    public List<ConversionCount> conversionByResult(LocalDate from, LocalDate to) {
        // 결과 유형이 있는 연락만 (문자만 보낸 모의 발송 행은 결과가 없다)
        return jdbc.query("""
                SELECT c.result AS k, COUNT(*) AS total, SUM(c.is_converted) AS converted
                """ + CONTACTS_IN_PERIOD + """
                   AND c.result IS NOT NULL
                 GROUP BY c.result
                """, times(from, to), (rs, i) -> conversion(rs.getString("k"), rs.getLong("total"), rs.getLong("converted")));
    }

    @Override
    public List<ConversionCount> conversionByMethod(LocalDate from, LocalDate to) {
        // 문자에는 2일차 뒤 보낸 안내 문자(모의 발송)도 들어간다 (정책 4-4 S-02)
        return jdbc.query("""
                SELECT c.method AS k, COUNT(*) AS total, SUM(c.is_converted) AS converted
                """ + CONTACTS_IN_PERIOD + """
                 GROUP BY c.method
                """, times(from, to), (rs, i) -> conversion(rs.getString("k"), rs.getLong("total"), rs.getLong("converted")));
    }

    private static ConversionCount conversion(String key, long total, long converted) {
        return new ConversionCount(key, total, converted);
    }

    private static Map<String, Object> dates(LocalDate from, LocalDate to) {
        return Map.of("from", Date.valueOf(from), "to", Date.valueOf(to));
    }

    private static Map<String, Object> times(LocalDate from, LocalDate to) {
        return Map.of("fromStart", Timestamp.valueOf(from.atStartOfDay()),
                "toEnd", Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
    }
}
