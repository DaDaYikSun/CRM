package com.dadayiksun.crm.customer.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.Guardian;
import com.dadayiksun.crm.customer.domain.Pet;
import com.dadayiksun.crm.customer.dto.GuardianDetail;
import com.dadayiksun.crm.customer.repository.GuardianRepository;
import com.dadayiksun.crm.customer.repository.PetRepository;

/**
 * 보호자 상세의 customer 몫 (C10, FR-CUS-008, POL-DATA-002). 전체 번호를 준다.
 * 품종 · 털 유형 · 등록 직원 이름은 이름만 읽는 조회 전용 쿼리다 (K17 ②). 판단은 다시 하지 않고 저장된 값만 읽는다.
 */
@Service
public class GuardianDetailReader {

    private final GuardianRepository guardians;
    private final PetRepository pets;
    private final NamedParameterJdbcTemplate jdbc;

    public GuardianDetailReader(GuardianRepository guardians, PetRepository pets, NamedParameterJdbcTemplate jdbc) {
        this.guardians = guardians;
        this.pets = pets;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public GuardianDetail read(Long guardianId) {
        Guardian g = guardians.findById(guardianId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        List<Pet> household = pets.findByGuardianIdOrderByPetIdAsc(guardianId);
        Map<Long, String> breeds = names("SELECT breed_id, name FROM breed WHERE breed_id IN (:ids)",
                household.stream().map(Pet::getBreedId).collect(Collectors.toSet()));
        Map<Long, String> coats = names("SELECT coat_type_id, name FROM coat_type WHERE coat_type_id IN (:ids)",
                household.stream().map(Pet::getCoatTypeId).collect(Collectors.toSet()));
        Map<Long, String> staff = names("SELECT staff_id, name FROM staff WHERE staff_id IN (:ids)",
                g.getCreatedBy() == null ? Set.of() : Set.of(g.getCreatedBy()));

        var info = new GuardianDetail.GuardianInfo(g.getGuardianId(), g.getName(), g.getPhone(), g.getSubPhone(),
                g.getPreferredContact(), g.getPhoneStatus(), g.getPhoneCheckedAt(), g.isPrivacyAgreed(),
                g.isSmsAgreed(), g.tags().stream().map(Enum::name).toList(), g.getContactTimeNote(),
                g.getCreatedAt().atZone(KstClock.ZONE).toOffsetDateTime(), staff.get(g.getCreatedBy()));
        var cards = household.stream()
                .map(p -> new GuardianDetail.PetCard(p.getPetId(), p.getName(), p.getStatus().name(),
                        breeds.get(p.getBreedId()), coats.get(p.getCoatTypeId()), p.getBirthDate(),
                        p.isBirthDateEstimated(), p.getContactHoldUntil()))
                .toList();
        return new GuardianDetail(info, cards);
    }

    private Map<Long, String> names(String sql, Set<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        if (!ids.isEmpty()) {
            jdbc.query(sql, new MapSqlParameterSource("ids", ids), rs -> {
                result.put(rs.getLong(1), rs.getString(2));
            });
        }
        return result;
    }
}
