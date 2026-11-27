package com.dadayiksun.crm.customer.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.dadayiksun.crm.customer.dto.OptionResponses.BreedOption;
import com.dadayiksun.crm.customer.dto.OptionResponses.CoatTypeOption;
import com.dadayiksun.crm.common.setting.SettingKey;
import com.dadayiksun.crm.common.setting.SettingService;
import com.dadayiksun.crm.customer.dto.OptionResponses.SettingsResponse;
import com.dadayiksun.crm.customer.dto.OptionResponses.VaccineTypeOption;

/**
 * 계약 C5 선택지 조회 (API 설계 3-2). 모든 역할이 부른다. 페이지로 나누지 않는다.
 *
 * <p>선택지 테이블은 묶음 대표가 아니라 Repository를 두지 않고 조회 전용 쿼리로 읽는다 (K16 ④, K17 ①).
 * {@code vaccine_type}은 주기 규칙(cycle) 쪽 테이블이다. 읽기만 한다 (K17 ②, PR에 적고 cycle 주인을 리뷰어로).
 * 숨긴 선택지({@code is_active = 0})는 주지 않는다 (DB 설계 4-4 · 4-5 · 4-6).
 */
@Service
public class OptionQueryService {

    private final JdbcTemplate jdbc;
    private final SettingService settings;

    public OptionQueryService(JdbcTemplate jdbc, SettingService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    /** 화면이 쓰는 기준값 읽기 (API 설계 3-2 {@code GET /api/options/settings}, 무지개다리 모달의 {보류 기간}, T6). */
    public SettingsResponse settings() {
        return new SettingsResponse(settings.getInt(SettingKey.MISS_THRESHOLD_DAYS),
                settings.getInt(SettingKey.RAINBOW_HOLD_DAYS));
    }

    public List<BreedOption> breeds() {
        return jdbc.query("""
                SELECT breed_id, name, default_coat_type_id
                  FROM breed
                 WHERE is_active = 1
                 ORDER BY sort_order, breed_id
                """,
                (rs, i) -> new BreedOption(rs.getLong("breed_id"), rs.getString("name"),
                        rs.getObject("default_coat_type_id", Long.class)));
    }

    public List<CoatTypeOption> coatTypes() {
        return jdbc.query("""
                SELECT coat_type_id, code, name
                  FROM coat_type
                 WHERE is_active = 1
                 ORDER BY sort_order, coat_type_id
                """,
                (rs, i) -> new CoatTypeOption(rs.getLong("coat_type_id"), rs.getString("code"), rs.getString("name")));
    }

    public List<VaccineTypeOption> vaccineTypes() {
        return jdbc.query("""
                SELECT vaccine_type_id, code, name, is_major
                  FROM vaccine_type
                 WHERE is_active = 1
                 ORDER BY sort_order, vaccine_type_id
                """,
                (rs, i) -> new VaccineTypeOption(rs.getLong("vaccine_type_id"), rs.getString("code"),
                        rs.getString("name"), rs.getBoolean("is_major")));
    }
}
