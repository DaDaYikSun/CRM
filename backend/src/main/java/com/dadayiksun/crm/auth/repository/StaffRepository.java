package com.dadayiksun.crm.auth.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;

/** 직원(staff) 묶음 대표 (DB 설계 4-1). 시드 계정 코드처럼 JdbcTemplate을 쓴다 (ENUM 칸 검증 문제를 피함). 직원 관리(#150)가 쓰기를 더한다. */
@Repository
public class StaffRepository {

    private static final String COLUMNS =
            "SELECT staff_id, login_id, password_hash, name, role, is_active, must_change_password FROM staff ";

    private static final RowMapper<Staff> MAPPER = (ResultSet rs, int row) -> mapRow(rs);

    private final JdbcTemplate jdbc;

    public StaffRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Staff> findByLoginId(String loginId) {
        return jdbc.query(COLUMNS + "WHERE login_id = ?", MAPPER, loginId).stream().findFirst();
    }

    public Optional<Staff> findById(Long staffId) {
        return jdbc.query(COLUMNS + "WHERE staff_id = ?", MAPPER, staffId).stream().findFirst();
    }

    public void updateLastLoginAt(Long staffId, LocalDateTime at) {
        jdbc.update("UPDATE staff SET last_login_at = ? WHERE staff_id = ?", at, staffId);
    }

    private static Staff mapRow(ResultSet rs) throws SQLException {
        return new Staff(rs.getLong("staff_id"), rs.getString("login_id"), rs.getString("password_hash"),
                rs.getString("name"), Role.valueOf(rs.getString("role")), rs.getBoolean("is_active"),
                rs.getBoolean("must_change_password"));
    }
}
