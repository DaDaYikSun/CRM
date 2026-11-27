package com.dadayiksun.crm.customer.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.Pet;
import com.dadayiksun.crm.customer.domain.PetProfileChangedEvent;
import com.dadayiksun.crm.customer.dto.PetProfilePatch;
import com.dadayiksun.crm.customer.dto.PetProfileResponse;
import com.dadayiksun.crm.customer.repository.PetRepository;

/**
 * {@code PATCH /api/pets/{id}} 아이 프로필 수정 (C10, FR-CUS-008, POL-REG-004 · 009).
 *
 * <p>생년월일 · 품종 · 털 유형 값이 실제로 바뀌면 '프로필 바뀜' 이벤트만 낸다. 예정일 재계산은 cycle이 같은 트랜잭션에서 한다 (K13).
 * 이름 · 체중만 바뀌면 이벤트를 내지 않는다. 생년월일을 보내면 '추정' 표시를 뗀다 (같은 날짜여도).
 */
@Service
public class PetProfileService {

    private static final int NAME_MAX = 30;
    /** DECIMAL(4,1): 999.9까지, 소수 한 자리 (DB 설계 4-3). */
    private static final BigDecimal WEIGHT_MAX = new BigDecimal("999.9");

    private final PetRepository pets;
    private final SelectableOptionChecker options;
    private final JdbcTemplate jdbc;
    private final KstClock clock;
    private final ApplicationEventPublisher events;

    public PetProfileService(PetRepository pets, SelectableOptionChecker options, JdbcTemplate jdbc, KstClock clock,
            ApplicationEventPublisher events) {
        this.pets = pets;
        this.options = options;
        this.jdbc = jdbc;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public PetProfileResponse update(Long petId, PetProfilePatch patch) {
        Pet pet = pets.findById(petId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        List<ErrorResponse.FieldError> errors = new ArrayList<>();

        String name = patch.name() == null ? null : patch.name().trim();
        if (name != null && name.isEmpty()) {
            throw ApiException.required("name", "아이 이름");
        }
        if (name != null && name.length() > NAME_MAX) {
            errors.add(invalid("name"));
        }
        boolean breedChanged = patch.breedId() != null && !patch.breedId().equals(pet.getBreedId());
        if (breedChanged && !options.isSelectableBreed(patch.breedId())) {
            errors.add(invalid("breedId"));
        }
        boolean coatChanged = patch.coatTypeId() != null && !patch.coatTypeId().equals(pet.getCoatTypeId());
        if (coatChanged && !options.isSelectableCoatType(patch.coatTypeId())) {
            errors.add(invalid("coatTypeId"));
        }
        // 미래 생년월일은 예정일을 몇 년 뒤로 밀어 연락 대상에서 조용히 빠지게 한다 (POL-REG-004)
        if (patch.birthDate() != null && patch.birthDate().isAfter(clock.today())) {
            errors.add(invalid("birthDate"));
        }
        BigDecimal w = patch.weightKg();
        if (w != null && (w.signum() <= 0 || w.compareTo(WEIGHT_MAX) > 0 || w.stripTrailingZeros().scale() > 1)) {
            errors.add(invalid("weightKg"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, errors.get(0).message(), List.copyOf(errors));
        }

        boolean birthChanged = patch.birthDate() != null && !patch.birthDate().equals(pet.getBirthDate());
        if (name != null) {
            pet.rename(name);
        }
        if (patch.birthDate() != null) {
            pet.correctBirthDate(patch.birthDate());
        }
        if (breedChanged) {
            pet.changeBreed(patch.breedId());
        }
        if (coatChanged) {
            pet.changeCoatType(patch.coatTypeId());
        }
        if (w != null) {
            pet.changeWeight(w);
        }
        if (birthChanged || breedChanged || coatChanged) {
            events.publishEvent(new PetProfileChangedEvent(petId));
        }
        return toResponse(pet);
    }

    private PetProfileResponse toResponse(Pet p) {
        String breed = jdbc.queryForObject("SELECT name FROM breed WHERE breed_id = ?", String.class, p.getBreedId());
        String coat = jdbc.queryForObject("SELECT name FROM coat_type WHERE coat_type_id = ?", String.class,
                p.getCoatTypeId());
        return new PetProfileResponse(new PetProfileResponse.PetBlock(p.getPetId(), p.getName(), "DOG",
                p.getStatus().name(), new PetProfileResponse.Named(p.getBreedId(), breed),
                new PetProfileResponse.Named(p.getCoatTypeId(), coat), p.getBirthDate(), p.isBirthDateEstimated(),
                p.getWeightKg(), p.getContactHoldUntil()));
    }

    private static ErrorResponse.FieldError invalid(String field) {
        return new ErrorResponse.FieldError(field, ErrorCode.VALIDATION_FALLBACK_MESSAGE);
    }
}
