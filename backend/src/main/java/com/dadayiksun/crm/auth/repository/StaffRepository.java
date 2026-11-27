package com.dadayiksun.crm.auth.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.auth.domain.StaffAccount;

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

    // ---- 직원 관리 (#150, FR-ADM-001). 쓰기는 auth 서비스만 한다 (K14 · K17) ----

    private static final RowMapper<StaffAccount> ACCOUNT_MAPPER = (rs, row) -> new StaffAccount(
            rs.getLong("staff_id"), rs.getString("login_id"), rs.getString("name"),
            Role.valueOf(rs.getString("role")), rs.getBoolean("is_active"),
            toLocal(rs.getTimestamp("last_login_at")), toLocal(rs.getTimestamp("deactivated_at")));

    /** 직원 목록: 사용 중 먼저, 그다음 등록 순 [제안]. 몇 명뿐이라 페이지로 나누지 않는다 (API 설계 3-10). */
    public List<StaffAccount> findAllAccounts() {
        return jdbc.query("SELECT staff_id, login_id, name, role, is_active, last_login_at, deactivated_at FROM staff "
                + "ORDER BY is_active DESC, staff_id", ACCOUNT_MAPPER);
    }

    public boolean existsByLoginId(String loginId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE login_id = ?", Integer.class, loginId);
        return count != null && count > 0;
    }

    /** 새 직원. 임시 비밀번호라 must_change_password = 1 (POL-ACC-010). */
    public Long insert(String loginId, String passwordHash, String name, Role role) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO staff (login_id, password_hash, name, role, must_change_password) VALUES (?, ?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, loginId);
            ps.setString(2, passwordHash);
            ps.setString(3, name);
            ps.setString(4, role.name());
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    public void updateRole(Long staffId, Role role) {
        jdbc.update("UPDATE staff SET role = ? WHERE staff_id = ?", role.name(), staffId);
    }

    /** 비활성화 (POL-ACC-004). 지우지 않는다. 남긴 기록의 created_by는 그대로다. */
    public void deactivate(Long staffId, LocalDateTime at) {
        jdbc.update("UPDATE staff SET is_active = 0, deactivated_at = ? WHERE staff_id = ? AND is_active = 1", at, staffId);
    }

    /** 다시 사용 [제안, API 설계 7장 12번]. */
    public void reactivate(Long staffId) {
        jdbc.update("UPDATE staff SET is_active = 1, deactivated_at = NULL WHERE staff_id = ?", staffId);
    }

    /** 새 비밀번호를 정함: 임시 비밀번호 상태를 푼다 (POL-ACC-010). */
    public void updatePassword(Long staffId, String passwordHash, boolean mustChangePassword) {
        jdbc.update("UPDATE staff SET password_hash = ?, must_change_password = ? WHERE staff_id = ?",
                passwordHash, mustChangePassword ? 1 : 0, staffId);
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
