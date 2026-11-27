package com.dadayiksun.crm.target.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dadayiksun.crm.target.domain.TargetCandidate;
import com.dadayiksun.crm.target.domain.TargetCandidate.ContactState;
import com.dadayiksun.crm.target.domain.TargetCandidate.ServiceType;

/**
 * {@link TargetCandidateQuery}의 MySQL 조회. 날짜는 백엔드가 한국 시간으로 정해 넘기고 {@code CURDATE()}는 쓰지 않는다
 * (DB 설계 1장). '오늘 처리'는 {@code contact_log.contacted_at}이 오늘 0시 이상 내일 0시 미만인 기록이다 (한국 시간 저장).
 */
@Repository
public class JdbcTargetCandidateQuery implements TargetCandidateQuery {

    private static final String SQL = """
            SELECT s.schedule_id, s.pet_id, p.name AS pet_name, p.guardian_id, g.name AS guardian_name, g.phone_last4,
                   s.service_type, vt.name AS vaccine_type_name, COALESCE(vt.sort_order, 0) AS vaccine_sort_order,
                   s.due_date, s.missed_at, s.contact_state, s.next_contact_date,
                   p.status AS pet_status, p.contact_hold_until, g.tag_no_contact,
                   EXISTS (SELECT 1 FROM contact_log c
                            WHERE c.schedule_id = s.schedule_id
                              AND c.contacted_at >= :todayStart AND c.contacted_at < :tomorrowStart) AS processed_today
              FROM schedule s
              JOIN pet p ON p.pet_id = s.pet_id
              JOIN guardian g ON g.guardian_id = p.guardian_id
              LEFT JOIN vaccine_type vt ON vt.vaccine_type_id = s.vaccine_type_id
             WHERE s.status = 'OPEN'
               AND (s.due_date <= :weekEnd OR s.missed_at IS NOT NULL)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTargetCandidateQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TargetCandidate> findOpenCandidates(LocalDate today, LocalDate weekEnd) {
        Map<String, Object> params = Map.of(
                "weekEnd", Date.valueOf(weekEnd),
                "todayStart", Timestamp.valueOf(today.atStartOfDay()),
                "tomorrowStart", Timestamp.valueOf(today.plusDays(1).atStartOfDay()));
        return jdbc.query(SQL, params, (rs, i) -> map(rs));
    }

    private static TargetCandidate map(ResultSet rs) throws SQLException {
        return new TargetCandidate(
                rs.getLong("schedule_id"),
                rs.getLong("pet_id"),
                rs.getString("pet_name"),
                rs.getLong("guardian_id"),
                rs.getString("guardian_name"),
                rs.getString("phone_last4"),
                ServiceType.valueOf(rs.getString("service_type")),
                rs.getString("vaccine_type_name"),
                rs.getInt("vaccine_sort_order"),
                date(rs, "due_date"),
                date(rs, "missed_at"),
                ContactState.valueOf(rs.getString("contact_state")),
                date(rs, "next_contact_date"),
                "ACTIVE".equals(rs.getString("pet_status")),
                date(rs, "contact_hold_until"),
                rs.getBoolean("tag_no_contact"),
                rs.getBoolean("processed_today"));
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }
}
