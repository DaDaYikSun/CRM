package com.dadayiksun.crm.visit.repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** {@link VisitRepository}의 MySQL 구현. 날짜 · 시각은 백엔드가 한국 시간으로 정해 넘긴다 (DB 설계 1장). */
@Repository
public class JdbcVisitRepository implements VisitRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcVisitRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> petStatus(long petId) {
        return jdbc.queryForList("SELECT status FROM pet WHERE pet_id = :id", Map.of("id", petId), String.class)
                .stream().findFirst();
    }

    @Override
    public boolean isSelectableVaccineType(long vaccineTypeId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM vaccine_type WHERE vaccine_type_id = :id AND is_active = 1",
                Map.of("id", vaccineTypeId), Integer.class);
        return n != null && n > 0;
    }

    @Override
    public boolean existsSameDay(long petId, String serviceType, Long vaccineTypeId, LocalDate visitDate) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM visit
                 WHERE pet_id = :petId AND service_type = :service AND visit_date = :date
                   AND vaccine_type_id <=> :typeId
                """, new MapSqlParameterSource("petId", petId).addValue("service", serviceType)
                .addValue("date", Date.valueOf(visitDate)).addValue("typeId", vaccineTypeId, Types.BIGINT),
                Integer.class);
        return n != null && n > 0;
    }

    @Override
    public long insert(Row r) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.getJdbcTemplate().update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO visit (pet_id, service_type, vaccine_type_id, visit_date, memo, created_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, r.petId());
            ps.setString(2, r.serviceType());
            if (r.vaccineTypeId() == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, r.vaccineTypeId());
            }
            ps.setDate(4, Date.valueOf(r.visitDate()));
            ps.setString(5, r.memo());
            ps.setLong(6, r.createdBy());
            ps.setTimestamp(7, Timestamp.valueOf(r.createdAt()));
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    @Override
    public List<LineRow> lines(long petId, Collection<String> serviceTypes) {
        if (serviceTypes.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT v.visit_id, v.service_type, vt.name AS vaccine_type_name, v.visit_date, v.memo,
                       st.name AS staff_name, v.created_at, v.is_edited
                  FROM visit v
                  JOIN staff st ON st.staff_id = v.created_by
                  LEFT JOIN vaccine_type vt ON vt.vaccine_type_id = v.vaccine_type_id
                 WHERE v.pet_id = :petId AND v.service_type IN (:types)
                """, new MapSqlParameterSource("petId", petId).addValue("types", serviceTypes),
                (rs, i) -> new LineRow(rs.getLong("visit_id"), rs.getString("service_type"),
                        rs.getString("vaccine_type_name"), rs.getDate("visit_date").toLocalDate(), rs.getString("memo"),
                        rs.getString("staff_name"), rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getBoolean("is_edited")));
    }
}
