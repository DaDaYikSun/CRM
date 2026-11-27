package com.dadayiksun.crm.contact.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dadayiksun.crm.contact.domain.ConversionRule;
import com.dadayiksun.crm.contact.dto.ContactPanelResponse;

/** {@link ContactQuery}의 MySQL 조회. 날짜는 백엔드가 한국 시간으로 정해 넘기고 {@code CURDATE()}는 쓰지 않는다 (DB 설계 1장). */
@Repository
public class JdbcContactQuery implements ContactQuery {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcContactQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ContactPanelResponse.Guardian> guardian(long guardianId) {
        return jdbc.query("""
                SELECT guardian_id, name, phone, sub_phone, preferred_contact, sms_agreed, phone_status,
                       tag_no_contact, tag_sms_only, tag_sensitive, tag_contact_time, contact_time_note
                  FROM guardian WHERE guardian_id = :id
                """, Map.of("id", guardianId), (rs, i) -> new ContactPanelResponse.Guardian(
                rs.getLong("guardian_id"), rs.getString("name"), rs.getString("phone"), rs.getString("sub_phone"),
                rs.getString("preferred_contact"), tags(rs), rs.getString("contact_time_note"),
                rs.getBoolean("sms_agreed"), rs.getString("phone_status"))).stream().findFirst();
    }

    /** 주의 태그 코드 (API 설계 1-4, DB 4-2 칸과 1:1) */
    private static List<String> tags(ResultSet rs) throws SQLException {
        List<String> tags = new ArrayList<>();
        if (rs.getBoolean("tag_no_contact")) tags.add("NO_CONTACT");
        if (rs.getBoolean("tag_sms_only")) tags.add("SMS_ONLY");
        if (rs.getBoolean("tag_sensitive")) tags.add("SENSITIVE");
        if (rs.getBoolean("tag_contact_time")) tags.add("CONTACT_TIME");
        return tags;
    }

    @Override
    public List<PanelItem> panelItems(long guardianId, LocalDate weekEnd) {
        return jdbc.query("""
                SELECT s.schedule_id, s.pet_id, p.name AS pet_name, s.service_type, vt.name AS vaccine_type_name,
                       s.due_date, s.missed_at, s.no_answer_count, s.next_contact_date
                  FROM schedule s
                  JOIN pet p ON p.pet_id = s.pet_id
                  LEFT JOIN vaccine_type vt ON vt.vaccine_type_id = s.vaccine_type_id
                 WHERE p.guardian_id = :id AND p.status = 'ACTIVE' AND s.status = 'OPEN'
                   AND (s.due_date <= :weekEnd OR s.missed_at IS NOT NULL)
                 ORDER BY p.pet_id, s.due_date, COALESCE(vt.sort_order, 0), s.schedule_id
                """, Map.of("id", guardianId, "weekEnd", Date.valueOf(weekEnd)), (rs, i) -> new PanelItem(
                rs.getLong("pet_id"), rs.getString("pet_name"), new ContactPanelResponse.Item(
                rs.getLong("schedule_id"), rs.getString("service_type"), rs.getString("vaccine_type_name"),
                rs.getDate("due_date").toLocalDate(), rs.getDate("missed_at") != null, rs.getInt("no_answer_count"),
                rs.getDate("next_contact_date") == null ? null : rs.getDate("next_contact_date").toLocalDate())));
    }

    @Override
    public Optional<ContactPanelResponse.TodayContact> todayContact(long guardianId, LocalDate today) {
        return jdbc.query("""
                SELECT st.name AS staff_name, c.contacted_at
                  FROM contact_log c JOIN staff st ON st.staff_id = c.created_by
                 WHERE c.guardian_id = :id AND c.contacted_at >= :from AND c.contacted_at < :to
                 ORDER BY c.contacted_at DESC, c.contact_log_id DESC LIMIT 1
                """, dayParams(guardianId, today), (rs, i) -> new ContactPanelResponse.TodayContact(
                rs.getString("staff_name"), rs.getTimestamp("contacted_at").toLocalDateTime().format(HH_MM)))
                .stream().findFirst();
    }

    private static MapSqlParameterSource dayParams(long guardianId, LocalDate day) {
        return new MapSqlParameterSource("id", guardianId)
                .addValue("from", Timestamp.valueOf(day.atStartOfDay()))
                .addValue("to", Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
    }

    @Override
    public long countHistory(long guardianId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM contact_log WHERE guardian_id = :id",
                Map.of("id", guardianId), Long.class);
        return n == null ? 0 : n;
    }

    /** 지난 연락 · 아이 타임라인이 같이 쓰는 줄. 항목 이름은 접종이면 접종 종류 이름, 미용이면 '미용' (#223) */
    private static final String HISTORY_SELECT = """
            SELECT c.contact_log_id, c.contacted_at, c.method, c.result, c.talked_with, c.memo, st.name AS staff_name,
                   c.created_by, c.is_edited, c.is_converted, c.is_mock_sms, p.name AS pet_name,
                   CASE WHEN s.service_type = 'GROOMING' THEN '미용' ELSE vt.name END AS item_name
              FROM contact_log c
              JOIN staff st ON st.staff_id = c.created_by
              JOIN pet p ON p.pet_id = c.pet_id
              JOIN schedule s ON s.schedule_id = c.schedule_id
              LEFT JOIN vaccine_type vt ON vt.vaccine_type_id = s.vaccine_type_id
            """;

    @Override
    public List<HistoryRow> history(long guardianId, int limit, long offset) {
        return jdbc.query(HISTORY_SELECT + """
                 WHERE c.guardian_id = :id
                 ORDER BY c.contacted_at DESC, c.contact_log_id DESC
                 LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("id", guardianId).addValue("limit", limit).addValue("offset", offset),
                (rs, i) -> historyRow(rs));
    }

    @Override
    public List<HistoryRow> petHistory(long petId) {
        return jdbc.query(HISTORY_SELECT + """
                 WHERE c.pet_id = :petId
                 ORDER BY c.contacted_at DESC, c.contact_log_id DESC
                """, Map.of("petId", petId), (rs, i) -> historyRow(rs));
    }

    private static HistoryRow historyRow(ResultSet rs) throws SQLException {
        return new HistoryRow(rs.getLong("contact_log_id"),
                rs.getTimestamp("contacted_at").toLocalDateTime(), rs.getString("method"),
                rs.getString("result"), rs.getString("talked_with"), rs.getString("memo"),
                rs.getString("staff_name"), rs.getLong("created_by"), rs.getBoolean("is_edited"),
                rs.getBoolean("is_converted"), rs.getBoolean("is_mock_sms"), rs.getString("pet_name"),
                rs.getString("item_name"));
    }

    @Override
    public List<GroupItem> groupItems(String contactGroupId) {
        return jdbc.query("""
                SELECT c.schedule_id, c.pet_id, p.name AS pet_name, c.guardian_id, g.phone, g.sms_agreed, c.result,
                       c.next_contact_date, s.no_answer_count, s.contact_state, s.next_contact_date AS prev_next_contact_date,
                       s.service_type, vt.name AS vaccine_type_name, COALESCE(vt.is_major, 0) AS is_major, s.due_date,
                       c.contacted_at, c.created_by
                  FROM contact_log c
                  JOIN schedule s ON s.schedule_id = c.schedule_id
                  JOIN pet p ON p.pet_id = c.pet_id
                  JOIN guardian g ON g.guardian_id = c.guardian_id
                  LEFT JOIN vaccine_type vt ON vt.vaccine_type_id = s.vaccine_type_id
                 WHERE c.contact_group_id = :groupId AND c.result IS NOT NULL
                 ORDER BY c.contact_log_id
                 FOR UPDATE OF s
                """, Map.of("groupId", contactGroupId), (rs, i) -> new GroupItem(rs.getLong("schedule_id"),
                rs.getLong("pet_id"), rs.getString("pet_name"), rs.getLong("guardian_id"), rs.getString("phone"),
                rs.getBoolean("sms_agreed"), rs.getString("result"),
                rs.getDate("next_contact_date") == null ? null : rs.getDate("next_contact_date").toLocalDate(),
                rs.getInt("no_answer_count"), rs.getString("contact_state"),
                rs.getDate("prev_next_contact_date") == null ? null : rs.getDate("prev_next_contact_date").toLocalDate(),
                rs.getString("service_type"), rs.getString("vaccine_type_name"), rs.getBoolean("is_major"),
                rs.getDate("due_date").toLocalDate(), rs.getTimestamp("contacted_at").toLocalDateTime(),
                rs.getLong("created_by")));
    }

    @Override
    public Optional<CompletedSchedule> completedBy(long visitId) {
        return jdbc.query("""
                SELECT s.schedule_id, s.due_date, s.miss_threshold_days, v.visit_date
                  FROM schedule s JOIN visit v ON v.visit_id = s.done_visit_id
                 WHERE s.done_visit_id = :visitId
                """, Map.of("visitId", visitId), (rs, i) -> new CompletedSchedule(rs.getLong("schedule_id"),
                rs.getDate("due_date").toLocalDate(), rs.getInt("miss_threshold_days"),
                rs.getDate("visit_date").toLocalDate())).stream().findFirst();
    }

    @Override
    public List<ConversionRule.Contact> contactsOf(long scheduleId) {
        return jdbc.query("SELECT contact_log_id, contacted_at FROM contact_log WHERE schedule_id = :id",
                Map.of("id", scheduleId), (rs, i) -> new ConversionRule.Contact(rs.getLong("contact_log_id"),
                        rs.getTimestamp("contacted_at").toLocalDateTime()));
    }

    @Override
    public String messageTemplate(String templateCode) {
        return jdbc.queryForObject("SELECT body FROM message_template WHERE template_code = :code",
                Map.of("code", templateCode), String.class);
    }

    @Override
    public List<OpenSchedule> openSchedules(Collection<Long> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT s.schedule_id, s.pet_id, p.guardian_id, p.status AS pet_status
                  FROM schedule s JOIN pet p ON p.pet_id = s.pet_id
                 WHERE s.schedule_id IN (:ids) AND s.status = 'OPEN'
                """, Map.of("ids", scheduleIds), (rs, i) -> new OpenSchedule(rs.getLong("schedule_id"),
                rs.getLong("pet_id"), rs.getLong("guardian_id"), "ACTIVE".equals(rs.getString("pet_status"))));
    }

    @Override
    public List<SmsTarget> smsTargets(Collection<Long> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT s.schedule_id, s.pet_id, p.name AS pet_name, p.guardian_id, g.phone, g.sms_agreed,
                       p.status AS pet_status, s.service_type, vt.name AS vaccine_type_name,
                       COALESCE(vt.is_major, 0) AS is_major, s.due_date
                  FROM schedule s
                  JOIN pet p ON p.pet_id = s.pet_id
                  JOIN guardian g ON g.guardian_id = p.guardian_id
                  LEFT JOIN vaccine_type vt ON vt.vaccine_type_id = s.vaccine_type_id
                 WHERE s.schedule_id IN (:ids) AND s.status = 'OPEN'
                """, Map.of("ids", scheduleIds), (rs, i) -> new SmsTarget(rs.getLong("schedule_id"),
                rs.getLong("pet_id"), rs.getString("pet_name"), rs.getLong("guardian_id"), rs.getString("phone"),
                rs.getBoolean("sms_agreed"), "ACTIVE".equals(rs.getString("pet_status")),
                rs.getString("service_type"), rs.getString("vaccine_type_name"), rs.getBoolean("is_major"),
                rs.getDate("due_date").toLocalDate()));
    }
}
