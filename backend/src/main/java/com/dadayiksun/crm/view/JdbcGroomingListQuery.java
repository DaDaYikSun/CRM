package com.dadayiksun.crm.view;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@link GroomingListQuery}의 MySQL 조회. cycle(schedule) · customer(pet · guardian · breed · coat_type) · visit을
 * 읽기만 한다 (R9). 번호는 뒤 4자리만 읽는다 (NFR-SEC-003). 누락은 저장된 {@code missed_at}만 본다 (T13).
 */
@Repository
public class JdbcGroomingListQuery implements GroomingListQuery {

    private static final String PET_COLUMNS = """
            p.pet_id, p.name AS pet_name, b.name AS breed_name, ct.name AS coat_type_name, p.birth_date,
            g.name AS guardian_name, g.phone_last4,
            g.tag_no_contact, g.tag_sms_only, g.tag_sensitive, g.tag_contact_time,
            p.groom_interval_days, p.groom_interval_basis,
            (SELECT MAX(lv.visit_date) FROM visit lv
              WHERE lv.pet_id = p.pet_id AND lv.service_type = 'GROOMING') AS last_groom_date
            """;

    private static final String PET_JOINS = """
            JOIN guardian g ON g.guardian_id = p.guardian_id
            JOIN breed b ON b.breed_id = p.breed_id
            JOIN coat_type ct ON ct.coat_type_id = p.coat_type_id
            """;

    private static final String SQL = "SELECT s.schedule_id, s.due_date, s.missed_at, 0 AS groomed_today, " + PET_COLUMNS
            + " FROM schedule s JOIN pet p ON p.pet_id = s.pet_id " + PET_JOINS
            + " WHERE s.service_type = 'GROOMING' AND s.status = 'OPEN' AND p.status = 'ACTIVE' AND s.due_date <= :dueUntil"
            + " UNION ALL "
            + "SELECT s.schedule_id, s.due_date, s.missed_at, 1 AS groomed_today, " + PET_COLUMNS
            + " FROM visit v JOIN pet p ON p.pet_id = v.pet_id " + PET_JOINS
            + " LEFT JOIN schedule s ON s.done_visit_id = v.visit_id"
            + " WHERE v.service_type = 'GROOMING' AND v.visit_date = :today AND p.status = 'ACTIVE'";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcGroomingListQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<GroomingRow> find(LocalDate dueUntil, LocalDate today) {
        Map<String, Object> params = Map.of("dueUntil", Date.valueOf(dueUntil), "today", Date.valueOf(today));
        return jdbc.query(SQL, params, (rs, i) -> map(rs));
    }

    private static GroomingRow map(ResultSet rs) throws SQLException {
        long scheduleId = rs.getLong("schedule_id");
        Long schedule = rs.wasNull() ? null : scheduleId;
        int days = rs.getInt("groom_interval_days");
        Integer interval = rs.wasNull() ? null : days;
        return new GroomingRow(
                rs.getLong("pet_id"),
                rs.getString("pet_name"),
                rs.getString("breed_name"),
                rs.getString("coat_type_name"),
                date(rs, "birth_date"),
                rs.getString("guardian_name"),
                rs.getString("phone_last4"),
                tags(rs),
                schedule,
                date(rs, "due_date"),
                rs.getDate("missed_at") != null,
                date(rs, "last_groom_date"),
                interval,
                rs.getString("groom_interval_basis"),
                rs.getInt("groomed_today") == 1);
    }

    /** customer {@code Guardian.tags()}와 같은 순서 · 이름 (API 설계 1장 고정값) */
    private static List<String> tags(ResultSet rs) throws SQLException {
        List<String> tags = new ArrayList<>();
        if (rs.getBoolean("tag_no_contact")) tags.add("NO_CONTACT");
        if (rs.getBoolean("tag_sms_only")) tags.add("SMS_ONLY");
        if (rs.getBoolean("tag_sensitive")) tags.add("SENSITIVE");
        if (rs.getBoolean("tag_contact_time")) tags.add("CONTACT_TIME");
        return tags;
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }
}
