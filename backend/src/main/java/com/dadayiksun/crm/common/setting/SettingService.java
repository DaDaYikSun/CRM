package com.dadayiksun.crm.common.setting;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 설정값 조회 (계약 C2, API 설계 4장). 읽기만 한다. 값을 바꾸는 저장은 백오피스 기준값 설정(초안 25)이 한다.
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
}
