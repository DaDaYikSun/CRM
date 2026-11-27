package com.dadayiksun.crm.contact.repository;

import java.sql.Date;
import java.sql.Timestamp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 기록한 직원 · 시간은 서버가 붙인다 (POL-CNT-003, NFR-DATA-002). */
@Repository
public class JdbcContactLogRepository implements ContactLogRepository {

    private final JdbcTemplate jdbc;

    public JdbcContactLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Row r) {
        jdbc.update("""
                INSERT INTO contact_log (contact_group_id, guardian_id, pet_id, schedule_id, method, result,
                                         talked_with, memo, next_contact_date, contacted_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, r.groupId(), r.guardianId(), r.petId(), r.scheduleId(), r.method(), r.result(), r.talkedWith(),
                r.memo(), r.nextContactDate() == null ? null : Date.valueOf(r.nextContactDate()),
                Timestamp.valueOf(r.contactedAt()), r.createdBy());
    }

    @Override
    public void markConverted(long contactLogId, long visitId) {
        jdbc.update("UPDATE contact_log SET is_converted = 1, converted_visit_id = ? WHERE contact_log_id = ?",
                visitId, contactLogId);
    }

    @Override
    public void insertMockSms(MockSmsRow r) {
        jdbc.update("""
                INSERT INTO contact_log (contact_group_id, guardian_id, pet_id, schedule_id, method, is_mock_sms,
                                         sms_template, sms_body, contacted_at, created_by)
                VALUES (?, ?, ?, ?, 'SMS', 1, ?, ?, ?, ?)
                """, r.groupId(), r.guardianId(), r.petId(), r.scheduleId(), r.template(), r.body(),
                Timestamp.valueOf(r.contactedAt()), r.createdBy());
    }
}
