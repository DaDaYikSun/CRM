package com.dadayiksun.crm.customer.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.dadayiksun.crm.common.audit.CurrentStaffAuditor;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 변경 이력(change_log) 중 customer가 남기는 것: 주의 태그(TAG_CHANGE)와 아이 상태(STATUS_CHANGE) (DB 설계 4-14, NFR-DATA-003).
 * 추가만 한다 (수정 · 삭제 금지, NFR-DATA-002). 바뀐 칸만 before / after에 넣는다.
 *
 * <p>[?] #142 common의 기록 도구를 같이 쓸지 정해지지 않아(이슈 #151 남은 질문) customer 안에 둔다. 정해지면 옮긴다.
 */
@Component
public class ChangeLogWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CurrentStaffAuditor staff;

    public ChangeLogWriter(JdbcTemplate jdbc, ObjectMapper json, CurrentStaffAuditor staff) {
        this.jdbc = jdbc;
        this.json = json;
        this.staff = staff;
    }

    public void write(String category, String targetType, Long targetId, Map<String, Object> before,
            Map<String, Object> after) {
        long staffId = staff.getCurrentAuditor().orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
        jdbc.update("INSERT INTO change_log (category, target_type, target_id, before_json, after_json, created_by)"
                + " VALUES (?, ?, ?, ?, ?, ?)", category, targetType, String.valueOf(targetId), toJson(before),
                toJson(after), staffId);
    }

    /**
     * 그 아이를 무지개다리로 바꾼 가장 최근 STATUS_CHANGE의 {@code after_json.holdUntil} (API 설계 3-3).
     * 되돌리기 행(status = ACTIVE)은 건너뛴다. 값이 없으면 빈 값.
     */
    public Optional<LocalDate> latestRainbowHoldUntil(Long petId) {
        var rows = jdbc.queryForList("SELECT after_json FROM change_log WHERE category = 'STATUS_CHANGE'"
                + " AND target_type = 'PET' AND target_id = ?"
                + " AND JSON_UNQUOTE(JSON_EXTRACT(after_json, '$.status')) = 'RAINBOW'"
                + " ORDER BY change_log_id DESC LIMIT 1", String.class, String.valueOf(petId));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode held = json.readTree(rows.get(0)).get("holdUntil");
            return held == null || held.isNull() ? Optional.empty() : Optional.of(LocalDate.parse(held.asText()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("change_log after_json을 읽지 못했습니다: pet " + petId, e);
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return json.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
