package com.dadayiksun.crm.search.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dadayiksun.crm.search.dto.SearchItem;
import com.dadayiksun.crm.search.service.SearchQuery;

/**
 * guardian · pet · breed를 읽기만 하는 조회 전용 쿼리 (K17 ②, customer 주인은 #145).
 * 판단(누락 등)은 하지 않는다.
 */
@Repository
public class SearchRepository {

    private static final String FROM = " FROM pet p JOIN guardian g ON g.guardian_id = p.guardian_id"
            + " JOIN breed b ON b.breed_id = p.breed_id WHERE ";

    private record Sibling(long petId, String name) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public SearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count(SearchQuery q) {
        Long n = jdbc.queryForObject("SELECT COUNT(*)" + FROM + where(q), params(q), Long.class);
        return n == null ? 0 : n;
    }

    /** 정렬 (이 이슈에서 정함): 살아 있는 아이 먼저, 아이 이름 · 보호자 이름 가나다순, 마지막은 pet_id */
    public List<SearchItem> find(SearchQuery q, int limit, long offset) {
        String sql = "SELECT p.pet_id, p.name AS pet_name, g.guardian_id, g.name AS guardian_name, g.phone_last4,"
                + " b.name AS breed_name, p.birth_date, p.birth_date_estimated, p.status" + FROM + where(q)
                + " ORDER BY (p.status = 'ACTIVE') DESC, p.name, g.name, p.pet_id LIMIT :limit OFFSET :offset";
        List<SearchItem> rows = jdbc.query(sql, params(q).addValue("limit", limit).addValue("offset", offset),
                (rs, i) -> new SearchItem(rs.getLong("pet_id"), rs.getString("pet_name"),
                        rs.getLong("guardian_id"), rs.getString("guardian_name"), rs.getString("phone_last4"),
                        rs.getString("breed_name"), rs.getObject("birth_date", LocalDate.class),
                        rs.getBoolean("birth_date_estimated"), rs.getString("status"), List.of()));
        if (rows.isEmpty()) {
            return rows;
        }
        // 같은 집 다른 아이 이름 (siblingNames). 결과 보호자들 것을 한 번에 읽는다.
        // 무지개다리 아이는 뺀다: 떠난 아이를 살아 있는 아이처럼 보여 주지 않게 (표시는 초안 21에서 다시 정한다)
        List<Long> guardianIds = rows.stream().map(SearchItem::guardianId).distinct().toList();
        Map<Long, List<Sibling>> byGuardian = new HashMap<>();
        jdbc.query("SELECT guardian_id, pet_id, name FROM pet WHERE guardian_id IN (:ids) AND status = 'ACTIVE' ORDER BY pet_id",
                new MapSqlParameterSource("ids", guardianIds),
                rs -> {
                    byGuardian.computeIfAbsent(rs.getLong("guardian_id"), k -> new ArrayList<>())
                            .add(new Sibling(rs.getLong("pet_id"), rs.getString("name")));
                });
        return rows.stream().map(r -> new SearchItem(r.petId(), r.petName(), r.guardianId(), r.guardianName(),
                r.phoneLast4(), r.breedName(), r.birthDate(), r.birthDateEstimated(), r.status(),
                byGuardian.getOrDefault(r.guardianId(), List.of()).stream()
                        .filter(s -> s.petId() != r.petId()).map(Sibling::name).toList())).toList();
    }

    private static String where(SearchQuery q) {
        if (q.phone()) {
            // 4자리면 번호 뒤 4자리, 그 밖은 번호 일부 ('010-1234' = '0101234')
            return q.text().length() == 4 ? "g.phone_last4 = :q" : "g.phone LIKE :like";
        }
        if (q.chosung()) {
            return "(p.name_chosung LIKE :like OR g.name_chosung LIKE :like)";
        }
        return "(p.name LIKE :like OR g.name LIKE :like)";
    }

    private static MapSqlParameterSource params(SearchQuery q) {
        String escaped = q.text().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return new MapSqlParameterSource("q", q.text())
                .addValue("like", q.phone() ? "%" + escaped + "%" : escaped + "%");
    }
}
