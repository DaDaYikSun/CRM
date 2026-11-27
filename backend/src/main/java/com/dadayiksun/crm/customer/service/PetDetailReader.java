package com.dadayiksun.crm.customer.service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.customer.domain.Guardian;
import com.dadayiksun.crm.customer.domain.Pet;
import com.dadayiksun.crm.customer.dto.PetDetail;
import com.dadayiksun.crm.customer.repository.GuardianRepository;
import com.dadayiksun.crm.customer.repository.PetRepository;

/**
 * 아이 상세의 customer 몫 (C9, FR-VIS-003 · FR-CUS-003, FR-CUS-005). 번호는 뒤 4자리만 준다 (NFR-SEC-003).
 * 품종 · 털 유형 이름 · 개인 미용 주기 캐시는 저장된 값만 읽는 조회 전용 쿼리다 (K17 ②).
 */
@Service
public class PetDetailReader {

    private final PetRepository pets;
    private final GuardianRepository guardians;
    private final JdbcTemplate jdbc;

    public PetDetailReader(PetRepository pets, GuardianRepository guardians, JdbcTemplate jdbc) {
        this.pets = pets;
        this.guardians = guardians;
        this.jdbc = jdbc;
    }

    /** 없는 아이면 404 NOT_FOUND (정책 4-2). */
    @Transactional(readOnly = true)
    public void requireExists(Long petId) {
        if (!pets.existsById(petId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public PetDetail read(Long petId) {
        Pet p = pets.findById(petId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Guardian g = guardians.findById(p.getGuardianId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String breed = jdbc.queryForObject("SELECT name FROM breed WHERE breed_id = ?", String.class, p.getBreedId());
        String coat = jdbc.queryForObject("SELECT name FROM coat_type WHERE coat_type_id = ?", String.class,
                p.getCoatTypeId());
        Map<String, Object> groom = jdbc.queryForMap(
                "SELECT groom_interval_days, groom_interval_basis FROM pet WHERE pet_id = ?", petId);
        PetDetail.GroomInterval interval = groom.get("groom_interval_days") == null ? null
                : new PetDetail.GroomInterval(((Number) groom.get("groom_interval_days")).intValue(),
                        String.valueOf(groom.get("groom_interval_basis")));
        String phone = g.getPhone();
        var pet = new PetDetail.PetBlock(p.getPetId(), p.getName(), "DOG", p.getStatus().name(),
                new PetDetail.Named(p.getBreedId(), breed), new PetDetail.Named(p.getCoatTypeId(), coat),
                p.getBirthDate(), p.isBirthDateEstimated(), p.getWeightKg(), p.getContactHoldUntil(), interval);
        var guardian = new PetDetail.GuardianBlock(g.getGuardianId(), g.getName(),
                phone.substring(Math.max(0, phone.length() - 4)), g.tags().stream().map(Enum::name).toList());
        List<PetDetail.Sibling> siblings = pets.findByGuardianIdOrderByPetIdAsc(g.getGuardianId()).stream()
                .filter(s -> !s.getPetId().equals(petId))
                .map(s -> new PetDetail.Sibling(s.getPetId(), s.getName(), s.getStatus().name()))
                .toList();
        return new PetDetail(pet, guardian, siblings);
    }
}
