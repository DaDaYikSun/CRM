package com.dadayiksun.crm.customer.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.BirthDate;
import com.dadayiksun.crm.customer.domain.Guardian;
import com.dadayiksun.crm.customer.domain.Pet;
import com.dadayiksun.crm.customer.domain.PetRegisteredEvent;
import com.dadayiksun.crm.customer.domain.PhoneNumber;
import com.dadayiksun.crm.customer.dto.AddPetResponse;
import com.dadayiksun.crm.customer.dto.GuardianInput;
import com.dadayiksun.crm.customer.dto.PetInput;
import com.dadayiksun.crm.customer.dto.RegisterGuardianRequest;
import com.dadayiksun.crm.customer.dto.RegisterGuardianResponse;
import com.dadayiksun.crm.customer.repository.GuardianRepository;
import com.dadayiksun.crm.customer.repository.PetRepository;

/**
 * 계약 C6 등록 (API 설계 3-3, FR-CUS-001 · 002).
 *
 * <ul>
 * <li>보호자와 첫 아이는 한 트랜잭션에서 함께 저장한다. 보호자만 저장되는 일이 없다 (SCR-S08-06).</li>
 * <li>기존 보호자에 아이를 붙일 때는 보호자 정보를 바꾸지 않는다 (SCR-S08-07).</li>
 * <li>아이를 저장하면 '아이 등록됨' 이벤트만 낸다. 첫 예정 일정은 cycle이 같은 트랜잭션에서 듣고 만든다 (K13, API 설계 4-1).</li>
 * <li>같은 요청 두 번은 {@code IdempotencyFilter}(#142, K15)가 막는다. 이 서비스는 따로 막지 않는다.</li>
 * <li>'기록한 직원'(created_by)은 {@code AuditEntity}가 세션의 직원 ID로 채운다 (C3, #143).</li>
 * </ul>
 */
@Service
public class PetRegistrationService {

    private final GuardianRepository guardianRepository;
    private final PetRepository petRepository;
    private final SelectableOptionChecker optionChecker;
    private final BirthDateResolver birthDateResolver;
    private final KstClock clock;
    private final ApplicationEventPublisher events;

    public PetRegistrationService(GuardianRepository guardianRepository, PetRepository petRepository,
            SelectableOptionChecker optionChecker, BirthDateResolver birthDateResolver, KstClock clock,
            ApplicationEventPublisher events) {
        this.guardianRepository = guardianRepository;
        this.petRepository = petRepository;
        this.optionChecker = optionChecker;
        this.birthDateResolver = birthDateResolver;
        this.clock = clock;
        this.events = events;
    }

    /** {@code POST /api/guardians}: 새 보호자와 첫 아이. */
    @Transactional
    public RegisterGuardianResponse registerGuardian(RegisterGuardianRequest request) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        GuardianInput g = request.guardian();
        String phone = PhoneNumber.normalize(g.phone());
        if (!PhoneNumber.isStorable(phone)) {
            errors.add(new ErrorResponse.FieldError("guardian.phone", ErrorCode.VALIDATION_FALLBACK_MESSAGE));
        }
        Optional<BirthDate> birth = checkPet(request.pet(), errors);
        throwIfAny(errors);

        Guardian guardian = guardianRepository.save(Guardian.register(g.name().trim(), phone,
                Boolean.TRUE.equals(g.privacyAgreed()), Boolean.TRUE.equals(g.smsAgreed()),
                clock.today(), clock.now()));
        Pet pet = savePet(guardian.getGuardianId(), request.pet(), birth.orElseThrow());
        return new RegisterGuardianResponse(guardian.getGuardianId(), pet.getPetId());
    }

    /** {@code POST /api/guardians/{id}/pets}: 기존 보호자에 아이 추가. 보호자 정보는 그대로. */
    @Transactional
    public AddPetResponse addPet(Long guardianId, PetInput input) {
        if (!guardianRepository.existsById(guardianId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        Optional<BirthDate> birth = checkPet(input, errors);
        throwIfAny(errors);
        Pet pet = savePet(guardianId, input, birth.orElseThrow());
        return new AddPetResponse(pet.getPetId());
    }

    /** 생년월일 · 선택지 검사 (POL-REG-001 · 002 · 004). 필수 칸 비었는지는 Controller의 @Valid가 먼저 본다. */
    private Optional<BirthDate> checkPet(PetInput pet, List<ErrorResponse.FieldError> errors) {
        Optional<BirthDate> birth = birthDateResolver.resolve(pet, errors);
        if (!optionChecker.isSelectableBreed(pet.breedId())) {
            errors.add(new ErrorResponse.FieldError("pet.breedId", ErrorCode.VALIDATION_FALLBACK_MESSAGE));
        }
        if (!optionChecker.isSelectableCoatType(pet.coatTypeId())) {
            errors.add(new ErrorResponse.FieldError("pet.coatTypeId", ErrorCode.VALIDATION_FALLBACK_MESSAGE));
        }
        return birth;
    }

    private Pet savePet(Long guardianId, PetInput input, BirthDate birth) {
        Pet pet = petRepository.save(Pet.register(guardianId, input.name().trim(), input.breedId(),
                input.coatTypeId(), birth));
        events.publishEvent(new PetRegisteredEvent(pet.getPetId()));
        return pet;
    }

    private static void throwIfAny(List<ErrorResponse.FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, errors.get(0).message(), List.copyOf(errors));
        }
    }
}
