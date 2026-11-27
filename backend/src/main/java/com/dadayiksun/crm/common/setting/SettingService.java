package com.dadayiksun.crm.common.setting;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 설정값 조회 (계약 C2, API 설계 4장). 값을 바꾸는 저장은 그 값의 주인 서비스가 검사한 뒤 {@link #updateInt}로 한다
 * (누락 기준 → cycle, 연락 보류 기간 → customer, #175 · R4).
 * 정책 숫자는 이 서비스로 읽고 코드에 박지 않는다 (CLAUDE.md 3번).
 */
@Service
public class SettingService {

    private final JdbcTemplate jdbc;

    public SettingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String getString(String key) {
        List<String> values = jdbc.queryForList(
                "SELECT setting_value FROM setting WHERE setting_key = ?", String.class, key);
        if (values.isEmpty()) {
            throw new IllegalStateException("setting 값이 없습니다: " + key);
        }
        return values.get(0);
    }

    public int getInt(String key) {
        return Integer.parseInt(getString(key).trim());
    }

    public LocalDate getDate(String key) {
        return LocalDate.parse(getString(key).trim());
    }

    /** 값만 바꾼다. 검사 · 변경 이력은 부르는 주인 서비스가 한다 (common에 도메인 규칙 없음, K16) */
    public void updateInt(String key, int value, long staffId) {
        int updated = jdbc.update("UPDATE setting SET setting_value = ?, updated_by = ? WHERE setting_key = ?",
                String.valueOf(value), staffId, key);
        if (updated == 0) {
            throw new IllegalStateException("setting 값이 없습니다: " + key);
        }
    }
}
