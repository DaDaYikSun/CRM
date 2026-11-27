package com.dadayiksun.crm.common.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 규칙 · 설정 변경 이력 (C17, FR-ADM-006, NFR-DATA-002, DB 설계 4-14). {@code change_log}에 {@code RULE_CHANGE}로 추가만 한다.
 * 백오피스 저장(#175 접종 규칙 · 미용 간격 · 기준값, #180 문구 · 병원 정보, #182 선택지)이 같이 쓴다. common에 둔다 (팀 결정 K22).
 * 바꾼 직원은 세션에서 읽는다. 바뀐 칸만 before / after에 넣고, 바뀐 칸이 없으면 쓰지 않는다.
 */
@Component
public class RuleChangeLogWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CurrentStaffAuditor staff;

    public RuleChangeLogWriter(JdbcTemplate jdbc, ObjectMapper json, CurrentStaffAuditor staff) {
        this.jdbc = jdbc;
        this.json = json;
        this.staff = staff;
    }

    /**
     * @param targetType change_log.target_type (VACCINE_RULE, VACCINE_TYPE, COAT_TYPE, BREED, SETTING, MESSAGE_TEMPLATE, REPORT_TEMPLATE)
     * @param targetId   바뀐 행 ID. setting은 setting_key, 문구는 template_code
     * @param before     바꾸기 전 값 (칸 이름 → 값)
     * @param after      바꾼 뒤 값. before와 같은 칸은 빼고 남긴다
     * @return 이력을 남겼으면 true (바뀐 칸이 있었다)
     */
    public boolean log(String targetType, String targetId, Map<String, ?> before, Map<String, ?> after) {
        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();
        after.forEach((field, value) -> {
            if (!Objects.equals(before.get(field), value)) {
                changedBefore.put(field, before.get(field));
                changedAfter.put(field, value);
            }
        });
        if (changedAfter.isEmpty()) {
            return false;
        }
        jdbc.update("INSERT INTO change_log (category, target_type, target_id, before_json, after_json, created_by)"
                + " VALUES ('RULE_CHANGE', ?, ?, ?, ?, ?)", targetType, targetId, toJson(changedBefore),
                toJson(changedAfter), currentStaffId());
        return true;
    }

    /** 바꾼 직원. 로그인 세션이 없으면 저장할 수 없다 (규칙 변경에 바꾼 사람이 남아야 한다, POL-DATA-008) */
    public long currentStaffId() {
        return staff.getCurrentAuditor().orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
    }

    private String toJson(Map<String, Object> map) {
        try {
            return json.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
