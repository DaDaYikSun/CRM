package com.dadayiksun.crm.cycle.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dadayiksun.crm.cycle.domain.PetProfile;
import com.dadayiksun.crm.cycle.domain.ServiceType;
import com.dadayiksun.crm.cycle.domain.VaccinePhase;
import com.dadayiksun.crm.cycle.domain.VaccineRule;
import com.dadayiksun.crm.cycle.domain.VisitInfo;

/** {@link CycleSourceQuery}의 MySQL 구현 (읽기만). */
@Repository
public class JdbcCycleSourceQuery implements CycleSourceQuery {

    private final JdbcTemplate jdbc;

    public JdbcCycleSourceQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PetProfile> petProfile(long petId) {
        return jdbc.query("""
                SELECT p.pet_id, p.birth_date, p.status, c.default_groom_weeks, DATE(p.created_at) AS registered_on
                  FROM pet p JOIN coat_type c ON c.coat_type_id = p.coat_type_id
                 WHERE p.pet_id = ?
                """, (rs, i) -> new PetProfile(
                        rs.getLong("pet_id"),
                        rs.getDate("birth_date").toLocalDate(),
                        "ACTIVE".equals(rs.getString("status")),
                        rs.getInt("default_groom_weeks") * 7,
                        rs.getDate("registered_on").toLocalDate()), petId)
                .stream().findFirst();
    }

    @Override
    public List<LocalDate> visitDates(long petId, ServiceType serviceType, Long vaccineTypeId) {
        String sql = "SELECT visit_date FROM visit WHERE pet_id = ? AND service_type = ? AND "
                + (vaccineTypeId == null ? "vaccine_type_id IS NULL" : "vaccine_type_id = ?")
                + " ORDER BY visit_date, visit_id";
        Object[] args = vaccineTypeId == null
                ? new Object[] {petId, serviceType.name()}
                : new Object[] {petId, serviceType.name(), vaccineTypeId};
        return jdbc.query(sql, (rs, i) -> rs.getDate(1).toLocalDate(), args);
    }

    @Override
    public Optional<VisitInfo> visit(long visitId) {
        return jdbc.query("SELECT visit_id, pet_id, service_type, vaccine_type_id, visit_date FROM visit WHERE visit_id = ?",
                (rs, i) -> new VisitInfo(
                        rs.getLong("visit_id"),
                        rs.getLong("pet_id"),
                        ServiceType.valueOf(rs.getString("service_type")),
                        rs.getObject("vaccine_type_id", Long.class),
                        rs.getDate("visit_date").toLocalDate()), visitId)
                .stream().findFirst();
    }

    @Override
    public List<Long> activePetIds() {
        return jdbc.queryForList("SELECT pet_id FROM pet WHERE status = 'ACTIVE' ORDER BY pet_id", Long.class);
    }

    @Override
    public Map<Long, List<VaccineRule>> activeRulesByType() {
        Map<Long, List<VaccineRule>> byType = new LinkedHashMap<>();
        jdbc.query("""
                SELECT r.vaccine_type_id, r.phase, r.age_from_days, r.age_to_days, r.interval_days, r.dose_count
                  FROM vaccine_rule r JOIN vaccine_type t ON t.vaccine_type_id = r.vaccine_type_id
                 WHERE r.is_active = 1 AND t.is_active = 1
                 ORDER BY t.sort_order, r.vaccine_type_id, r.age_from_days
                """, rs -> {
            byType.computeIfAbsent(rs.getLong("vaccine_type_id"), k -> new ArrayList<>()).add(new VaccineRule(
                    VaccinePhase.valueOf(rs.getString("phase")),
                    rs.getInt("age_from_days"),
                    rs.getObject("age_to_days", Integer.class),
                    rs.getInt("interval_days"),
                    rs.getObject("dose_count", Integer.class)));
        });
        return byType;
    }

    @Override
    public Set<Long> selectiveTypeIds() {
        // PREVENTIVE 행은 쓰는지(is_active)와 상관없이 본다: 예방 행을 잠시 꺼도 예방 처치 종류가 선택 접종으로 바뀌지 않게 (PR #239 리뷰)
        return new HashSet<>(jdbc.queryForList("""
                SELECT t.vaccine_type_id FROM vaccine_type t
                 WHERE t.is_major = 0
                   AND NOT EXISTS (SELECT 1 FROM vaccine_rule r
                                    WHERE r.vaccine_type_id = t.vaccine_type_id AND r.phase = 'PREVENTIVE')
                """, Long.class));
    }
}
