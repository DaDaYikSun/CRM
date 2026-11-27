package com.dadayiksun.crm.cycle.repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.dadayiksun.crm.cycle.domain.GroomInterval;
import com.dadayiksun.crm.cycle.domain.Schedule;
import com.dadayiksun.crm.cycle.domain.ServiceType;

/** {@link ScheduleRepository}의 MySQL 구현. 날짜 더하기는 DATE_ADD, '오늘'은 백엔드가 넘긴다 (DB 설계 1장). */
@Repository
public class JdbcScheduleRepository implements ScheduleRepository {

    private static final String COLUMNS = """
            schedule_id, pet_id, service_type, vaccine_type_id, due_date, status, miss_threshold_days,
            done_visit_id, done_date, gap_days, missed_at, contact_state, no_answer_count, next_contact_date
            """;

    private final JdbcTemplate jdbc;

    public JdbcScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Schedule> findById(long scheduleId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM schedule WHERE schedule_id = ?", (rs, i) -> map(rs), scheduleId)
                .stream().findFirst();
    }

    @Override
    public Optional<Schedule> findOpen(long petId, ServiceType serviceType, Long vaccineTypeId) {
        String sql = "SELECT " + COLUMNS + " FROM schedule WHERE status = 'OPEN' AND pet_id = ? AND service_type = ? AND "
                + (vaccineTypeId == null ? "vaccine_type_id IS NULL" : "vaccine_type_id = ?");
        Object[] args = vaccineTypeId == null
                ? new Object[] {petId, serviceType.name()}
                : new Object[] {petId, serviceType.name(), vaccineTypeId};
        return jdbc.query(sql, (rs, i) -> map(rs), args).stream().findFirst();
    }

    @Override
    public List<Schedule> findOpenByPet(long petId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM schedule WHERE status = 'OPEN' AND pet_id = ? ORDER BY due_date",
                (rs, i) -> map(rs), petId);
    }

    @Override
    public List<Schedule> findByPet(long petId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM schedule WHERE pet_id = ? ORDER BY due_date, schedule_id",
                (rs, i) -> map(rs), petId);
    }

    @Override
    public long insertOpen(long petId, ServiceType serviceType, Long vaccineTypeId, LocalDate dueDate, int missThresholdDays) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO schedule (pet_id, service_type, vaccine_type_id, due_date, status, miss_threshold_days)
                    VALUES (?, ?, ?, ?, 'OPEN', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, petId);
            ps.setString(2, serviceType.name());
            ps.setObject(3, vaccineTypeId);
            ps.setDate(4, Date.valueOf(dueDate));
            ps.setInt(5, missThresholdDays);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    @Override
    public void complete(long scheduleId, long visitId, LocalDate doneDate, int gapDays, LocalDate missedAt) {
        jdbc.update("""
                UPDATE schedule SET status = 'DONE', done_visit_id = ?, done_date = ?, gap_days = ?, missed_at = ?
                 WHERE schedule_id = ? AND status = 'OPEN'
                """, visitId, Date.valueOf(doneDate), gapDays, missedAt == null ? null : Date.valueOf(missedAt), scheduleId);
    }

    @Override
    public void clearMissedAt(long scheduleId) {
        jdbc.update("UPDATE schedule SET missed_at = NULL WHERE schedule_id = ?", scheduleId);
    }

    @Override
    public void updateDueDate(long scheduleId, LocalDate dueDate) {
        jdbc.update("UPDATE schedule SET due_date = ? WHERE schedule_id = ? AND status = 'OPEN'",
                Date.valueOf(dueDate), scheduleId);
    }

    @Override
    public void voidSchedule(long scheduleId) {
        jdbc.update("UPDATE schedule SET status = 'VOID' WHERE schedule_id = ? AND status = 'OPEN'", scheduleId);
    }

    @Override
    public int markMissed(LocalDate today) {
        // 무지개다리 아이의 지난 OPEN 주기는 판정하지 않는다 (재방문 대상이 아님, POL-END-002 · 누락률에서 빠짐)
        return jdbc.update("""
                UPDATE schedule s JOIN pet p ON p.pet_id = s.pet_id
                   SET s.missed_at = DATE_ADD(s.due_date, INTERVAL s.miss_threshold_days + 1 DAY)
                 WHERE s.status = 'OPEN' AND s.missed_at IS NULL AND p.status = 'ACTIVE'
                   AND DATE_ADD(s.due_date, INTERVAL s.miss_threshold_days DAY) < ?
                """, Date.valueOf(today));
    }

    @Override
    public void updateContact(long scheduleId, String contactState, int noAnswerCount, LocalDate nextContactDate) {
        jdbc.update("UPDATE schedule SET contact_state = ?, no_answer_count = ?, next_contact_date = ? WHERE schedule_id = ?",
                contactState, noAnswerCount, nextContactDate == null ? null : Date.valueOf(nextContactDate), scheduleId);
    }

    @Override
    public void saveGroomInterval(long petId, GroomInterval interval) {
        jdbc.update("UPDATE pet SET groom_interval_days = ?, groom_interval_basis = ? WHERE pet_id = ?",
                interval.days(), interval.basis().name(), petId);
    }

    private static Schedule map(ResultSet rs) throws SQLException {
        return new Schedule(
                rs.getLong("schedule_id"),
                rs.getLong("pet_id"),
                ServiceType.valueOf(rs.getString("service_type")),
                (Long) rs.getObject("vaccine_type_id", Long.class),
                date(rs, "due_date"),
                Schedule.Status.valueOf(rs.getString("status")),
                rs.getInt("miss_threshold_days"),
                (Long) rs.getObject("done_visit_id", Long.class),
                date(rs, "done_date"),
                (Integer) rs.getObject("gap_days", Integer.class),
                date(rs, "missed_at"),
                rs.getString("contact_state"),
                rs.getInt("no_answer_count"),
                date(rs, "next_contact_date"));
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }
}
