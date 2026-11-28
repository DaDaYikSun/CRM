package com.dadayiksun.crm.common.audit;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.common.web.PageQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * B-06 설정 변경 이력 조회 (FR-ADM-006, API 설계 3-10 {@code GET /api/admin/rule-history}, #182).
 * {@link RuleChangeLogWriter}가 남긴 {@code change_log}의 RULE_CHANGE 행을 최근 순으로 읽기만 한다 (조회 전용, K17).
 * 대상 이름(품종 · 털 유형 · 접종 종류)은 화면이 읽기 쉽게 붙인다. 지운 대상은 없으므로(숨김만) 이름이 늘 있다.
 */
@Component
public class RuleHistoryQuery {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RuleHistoryQuery(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * @param targetName 품종 · 털 유형 · 접종 종류 이름. 접종 규칙이면 그 규칙의 접종 종류 이름. setting · 문구 · 양식은 null(화면이 키로 이름을 붙임)
     */
    public record Item(long changeLogId, String targetType, String targetId, String targetName, JsonNode before,
                       JsonNode after, String staffName, OffsetDateTime changedAt) {
    }

    public PageResponse<Item> page(PageQuery page) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM change_log WHERE category = 'RULE_CHANGE'", Long.class);
        long count = total == null ? 0 : total;
        if (page.isBeyond(count)) {
            return PageResponse.of(List.of(), page, count);
        }
        // target_id는 문자(설정 키도 들어감)라 숫자 대상만 이름을 붙인다
        List<Item> items = jdbc.query("""
                SELECT c.change_log_id, c.target_type, c.target_id, c.before_json, c.after_json, c.created_at,
                       s.name AS staff_name,
                       CASE c.target_type
                         WHEN 'BREED' THEN b.name
                         WHEN 'COAT_TYPE' THEN ct.name
                         WHEN 'VACCINE_TYPE' THEN vt.name
                         WHEN 'VACCINE_RULE' THEN rvt.name
                       END AS target_name
                  FROM change_log c
                  JOIN staff s ON s.staff_id = c.created_by
                  LEFT JOIN breed b ON c.target_type = 'BREED' AND b.breed_id = c.target_id
                  LEFT JOIN coat_type ct ON c.target_type = 'COAT_TYPE' AND ct.coat_type_id = c.target_id
                  LEFT JOIN vaccine_type vt ON c.target_type = 'VACCINE_TYPE' AND vt.vaccine_type_id = c.target_id
                  LEFT JOIN vaccine_rule r ON c.target_type = 'VACCINE_RULE' AND r.vaccine_rule_id = c.target_id
                  LEFT JOIN vaccine_type rvt ON rvt.vaccine_type_id = r.vaccine_type_id
                 WHERE c.category = 'RULE_CHANGE'
                 ORDER BY c.created_at DESC, c.change_log_id DESC
                 LIMIT ? OFFSET ?
                """, (rs, i) -> new Item(rs.getLong("change_log_id"), rs.getString("target_type"),
                rs.getString("target_id"), rs.getString("target_name"), read(rs.getString("before_json")),
                read(rs.getString("after_json")), rs.getString("staff_name"),
                rs.getTimestamp("created_at").toLocalDateTime().atZone(KstClock.ZONE).toOffsetDateTime()),
                page.limit(), page.offset());
        return PageResponse.of(items, page, count);
    }

    private JsonNode read(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return json.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("change_log JSON을 읽지 못했습니다", e);
        }
    }
}
