package com.dadayiksun.crm.customer.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.audit.RuleChangeLogWriter;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;
import com.dadayiksun.crm.customer.dto.OptionAdminDtos.BreedInput;
import com.dadayiksun.crm.customer.dto.OptionAdminDtos.BreedItem;
import com.dadayiksun.crm.customer.dto.OptionAdminDtos.CoatTypeInput;
import com.dadayiksun.crm.customer.dto.OptionAdminDtos.CoatTypeItem;

/**
 * B-03 품종 · 털 유형 선택지 관리 (FR-ADM-005, POL-REG-002, API 설계 3-10, #182). 주인은 customer (R4).
 *
 * <ul>
 *   <li>지우지 않고 isActive = false로 숨긴다. 이미 그 값을 쓰는 아이는 그대로 보인다 (DB 설계 7장, #182 남은 질문)</li>
 *   <li>같은 이름이 있으면 409 OPTION_DUPLICATE (정책 4-2 '같은 선택지')</li>
 *   <li>바뀐 칸은 change_log에 RULE_CHANGE로 남긴다 (FR-ADM-006, C17). 바꾼 직원은 행의 updated_by에도</li>
 *   <li>선택지 테이블은 묶음 대표가 아니라 Repository 없이 쿼리로 읽고 쓴다 (K16 ④, {@link OptionQueryService}와 같음)</li>
 * </ul>
 */
@Service
@Transactional
public class OptionAdminService {

    /** DB 설계 4-4 breed.name · 4-3 coat_type.name 길이 */
    static final int BREED_NAME_MAX = 50;
    static final int COAT_NAME_MAX = 20;
    /** coat_type.default_groom_weeks는 TINYINT (DB 4-3) */
    static final int MAX_WEEKS = 127;

    private final JdbcTemplate jdbc;
    private final RuleChangeLogWriter changeLog;

    public OptionAdminService(JdbcTemplate jdbc, RuleChangeLogWriter changeLog) {
        this.jdbc = jdbc;
        this.changeLog = changeLog;
    }

    // ---- 품종

    @Transactional(readOnly = true)
    public List<BreedItem> breeds() {
        return jdbc.query("SELECT breed_id, name, default_coat_type_id, sort_order, is_active FROM breed"
                + " ORDER BY sort_order, breed_id", (rs, i) -> new BreedItem(rs.getLong("breed_id"), rs.getString("name"),
                rs.getObject("default_coat_type_id", Long.class), rs.getInt("sort_order"), rs.getBoolean("is_active")));
    }

    public BreedItem createBreed(BreedInput in) {
        String name = name(in.name(), BREED_NAME_MAX);
        Long coat = Boolean.TRUE.equals(in.clearDefaultCoatType()) ? null : coatTypeIdOrNull(in.defaultCoatTypeId());
        requireUniqueBreed(name, null);
        int sort = in.sortOrder() != null ? sortOrder(in.sortOrder())
                : jdbc.queryForObject("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM breed", Integer.class);
        boolean active = in.isActive() == null || in.isActive();
        long staffId = changeLog.currentStaffId();
        try {
            jdbc.update("INSERT INTO breed (name, default_coat_type_id, sort_order, is_active, created_by, updated_by)"
                    + " VALUES (?, ?, ?, ?, ?, ?)", name, coat, sort, active, staffId, staffId);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.OPTION_DUPLICATE); // 같은 이름을 동시에 넣은 경우 (uk_breed_species_name)
        }
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        BreedItem created = new BreedItem(id, name, coat, sort, active);
        changeLog.log("BREED", String.valueOf(id), values((BreedItem) null), values(created));
        return created;
    }

    public BreedItem updateBreed(long breedId, BreedInput in) {
        // FOR UPDATE: 같은 행의 동시 PATCH가 서로의 저장을 덮어쓰지 않게, 읽기 · 이력 · UPDATE를 한 잠금 안에 둔다 (PR #256 리뷰)
        BreedItem before = jdbc.query("SELECT breed_id, name, default_coat_type_id, sort_order, is_active FROM breed"
                + " WHERE breed_id = ? FOR UPDATE", (rs, i) -> new BreedItem(rs.getLong("breed_id"), rs.getString("name"),
                rs.getObject("default_coat_type_id", Long.class), rs.getInt("sort_order"), rs.getBoolean("is_active")), breedId)
                .stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String name = in.name() == null ? before.name() : name(in.name(), BREED_NAME_MAX);
        Long coat = Boolean.TRUE.equals(in.clearDefaultCoatType()) ? null
                : in.defaultCoatTypeId() == null ? before.defaultCoatTypeId() : coatTypeIdOrNull(in.defaultCoatTypeId());
        int sort = in.sortOrder() == null ? before.sortOrder() : sortOrder(in.sortOrder());
        boolean active = in.isActive() == null ? before.isActive() : in.isActive();
        if (!name.equals(before.name())) {
            requireUniqueBreed(name, breedId);
        }
        BreedItem after = new BreedItem(breedId, name, coat, sort, active);
        if (changeLog.log("BREED", String.valueOf(breedId), values(before), values(after))) {
            try {
                jdbc.update("UPDATE breed SET name = ?, default_coat_type_id = ?, sort_order = ?, is_active = ?,"
                        + " updated_by = ? WHERE breed_id = ?", name, coat, sort, active, changeLog.currentStaffId(), breedId);
            } catch (DuplicateKeyException e) {
                throw new ApiException(ErrorCode.OPTION_DUPLICATE);
            }
        }
        return after;
    }

    // ---- 털 유형

    @Transactional(readOnly = true)
    public List<CoatTypeItem> coatTypes() {
        return jdbc.query("SELECT coat_type_id, code, name, default_groom_weeks, sort_order, is_active FROM coat_type"
                + " ORDER BY sort_order, coat_type_id", (rs, i) -> new CoatTypeItem(rs.getLong("coat_type_id"),
                rs.getString("code"), rs.getString("name"), rs.getInt("default_groom_weeks"), rs.getInt("sort_order"),
                rs.getBoolean("is_active")));
    }

    /** 코드는 서버가 만든다 (COAT_번호). 직원이 코드를 정할 일이 없게 (요구사항 근거 없음, 설계 결정, #182) */
    public CoatTypeItem createCoatType(CoatTypeInput in) {
        String name = name(in.name(), COAT_NAME_MAX);
        Integer weeks = in.defaultGroomWeeks();
        if (weeks == null || weeks <= 0 || weeks > MAX_WEEKS) {
            throw new ApiException(ErrorCode.RULE_VALUE_INVALID); // POL-ERR-006
        }
        requireUniqueCoatName(name, null);
        int sort = in.sortOrder() != null ? sortOrder(in.sortOrder())
                : jdbc.queryForObject("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM coat_type", Integer.class);
        boolean active = in.isActive() == null || in.isActive();
        long next = jdbc.queryForObject("SELECT COALESCE(MAX(coat_type_id), 0) + 1 FROM coat_type", Long.class);
        String code = "COAT_" + next;
        long staffId = changeLog.currentStaffId();
        try {
            jdbc.update("INSERT INTO coat_type (code, name, default_groom_weeks, sort_order, is_active, created_by, updated_by)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?)", code, name, weeks, sort, active, staffId, staffId);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.OPTION_DUPLICATE); // 동시에 추가해 코드가 겹친 경우 (uk_coat_type_code). 다시 누르면 된다
        }
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        CoatTypeItem created = new CoatTypeItem(id, code, name, weeks, sort, active);
        changeLog.log("COAT_TYPE", String.valueOf(id), values((CoatTypeItem) null), values(created));
        return created;
    }

    /** 이름 · 순서 · 숨기기만. 간격은 B-02에서 (예정일 다시 계산, #175) */
    public CoatTypeItem updateCoatType(long coatTypeId, CoatTypeInput in) {
        // FOR UPDATE: 같은 행의 동시 PATCH가 서로의 저장을 덮어쓰지 않게 (PR #256 리뷰).
        // 이름 검사(requireUniqueCoatName)가 표 전체를 잠그므로, 교착을 피하려고 처음부터 표 전체를 같은 순서로 잠근다
        jdbc.queryForList("SELECT coat_type_id FROM coat_type ORDER BY coat_type_id FOR UPDATE", Long.class);
        CoatTypeItem before = jdbc.query("SELECT coat_type_id, code, name, default_groom_weeks, sort_order, is_active"
                + " FROM coat_type WHERE coat_type_id = ? FOR UPDATE", (rs, i) -> new CoatTypeItem(rs.getLong("coat_type_id"),
                rs.getString("code"), rs.getString("name"), rs.getInt("default_groom_weeks"), rs.getInt("sort_order"),
                rs.getBoolean("is_active")), coatTypeId)
                .stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String name = in.name() == null ? before.name() : name(in.name(), COAT_NAME_MAX);
        int sort = in.sortOrder() == null ? before.sortOrder() : sortOrder(in.sortOrder());
        boolean active = in.isActive() == null ? before.isActive() : in.isActive();
        if (!name.equals(before.name())) {
            requireUniqueCoatName(name, coatTypeId);
        }
        CoatTypeItem after = new CoatTypeItem(coatTypeId, before.code(), name, before.defaultGroomWeeks(), sort, active);
        if (changeLog.log("COAT_TYPE", String.valueOf(coatTypeId), values(before), values(after))) {
            jdbc.update("UPDATE coat_type SET name = ?, sort_order = ?, is_active = ?, updated_by = ? WHERE coat_type_id = ?",
                    name, sort, active, changeLog.currentStaffId(), coatTypeId);
        }
        return after;
    }

    // ---- 검사

    private static String name(String raw, int max) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw fieldError("name", ErrorCode.required("이름"));
        }
        if (name.length() > max) {
            throw fieldError("name", ErrorCode.VALIDATION_FALLBACK_MESSAGE);
        }
        return name;
    }

    private static int sortOrder(int value) {
        if (value < 0) {
            throw fieldError("sortOrder", ErrorCode.VALIDATION_FALLBACK_MESSAGE);
        }
        return value;
    }

    private Long coatTypeIdOrNull(Long id) {
        if (id == null) {
            return null;
        }
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM coat_type WHERE coat_type_id = ?", Integer.class, id);
        if (n == null || n == 0) {
            throw fieldError("defaultCoatTypeId", ErrorCode.VALIDATION_FALLBACK_MESSAGE);
        }
        return id;
    }

    /** 숨긴 것과도 겹치면 막는다 (UK가 숨김 여부를 가리지 않는다, uk_breed_species_name) */
    private void requireUniqueBreed(String name, Long exceptId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM breed WHERE species = 'DOG' AND name = ? AND breed_id <> ?",
                Integer.class, name, exceptId == null ? -1L : exceptId);
        if (n != null && n > 0) {
            throw new ApiException(ErrorCode.OPTION_DUPLICATE);
        }
    }

    /**
     * coat_type.name에는 유일 키가 없다(DB 4-3). 잠금 조회(FOR UPDATE)로 읽어 같은 이름 검사 · 저장을 한 줄로 세운다:
     * name에 인덱스가 없어 표 전체를 잠그므로, 동시에 같은 이름으로 바꾸는 두 요청 중 뒤의 것이 앞의 커밋을 보고 막힌다 (PR #256 리뷰).
     * 털 유형은 몇 개뿐이라 잠금 범위가 작다. shortcut: 행이 많아지면 새 마이그레이션으로 name 유일 키를 건다
     */
    private void requireUniqueCoatName(String name, Long exceptId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM coat_type WHERE name = ? AND coat_type_id <> ? FOR UPDATE",
                Integer.class, name, exceptId == null ? -1L : exceptId);
        if (n != null && n > 0) {
            throw new ApiException(ErrorCode.OPTION_DUPLICATE);
        }
    }

    private static ApiException fieldError(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message, List.of(new ErrorResponse.FieldError(field, message)));
    }

    /** 이력에 남길 칸. null이면 새로 만든 것이라 '바꾸기 전'이 모두 비어 있다 */
    private static Map<String, Object> values(BreedItem b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", b == null ? null : b.name());
        m.put("defaultCoatTypeId", b == null ? null : b.defaultCoatTypeId());
        m.put("sortOrder", b == null ? null : b.sortOrder());
        m.put("isActive", b == null ? null : b.isActive());
        return m;
    }

    private static Map<String, Object> values(CoatTypeItem c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c == null ? null : c.name());
        m.put("defaultGroomWeeks", c == null ? null : c.defaultGroomWeeks());
        m.put("sortOrder", c == null ? null : c.sortOrder());
        m.put("isActive", c == null ? null : c.isActive());
        return m;
    }
}
