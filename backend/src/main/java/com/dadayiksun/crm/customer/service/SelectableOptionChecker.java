package com.dadayiksun.crm.customer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 등록 입력의 품종 · 털 유형 ID가 지금 고를 수 있는 선택지인지 (POL-REG-002).
 * 선택지 목록({@link OptionQueryService})과 같은 조건(있고, {@code is_active = 1})으로 본다. 조회 전용 쿼리 (K16 ④).
 */
@Component
public class SelectableOptionChecker {

    private final JdbcTemplate jdbc;

    public SelectableOptionChecker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isSelectableBreed(long breedId) {
        return exists("SELECT COUNT(*) FROM breed WHERE breed_id = ? AND is_active = 1", breedId);
    }

    public boolean isSelectableCoatType(long coatTypeId) {
        return exists("SELECT COUNT(*) FROM coat_type WHERE coat_type_id = ? AND is_active = 1", coatTypeId);
    }

    private boolean exists(String sql, long id) {
        Integer count = jdbc.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }
}
