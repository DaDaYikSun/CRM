package com.dadayiksun.crm.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.Guardian;
import com.dadayiksun.crm.customer.domain.Pet;
import com.dadayiksun.crm.customer.domain.PetRegisteredEvent;
import com.dadayiksun.crm.customer.dto.GuardianInput;
import com.dadayiksun.crm.customer.dto.PetInput;
import com.dadayiksun.crm.customer.dto.RegisterGuardianRequest;
import com.dadayiksun.crm.customer.repository.GuardianRepository;
import com.dadayiksun.crm.customer.repository.PetRepository;

/** 등록 C6 저장 규칙 (FR-CUS-001 · 002, POL-REG-001~005 · 010, K13). */
class PetRegistrationServiceTest {

    /** 한국 시간 2026-11-26 10:00. */
    private static final Instant NOW = Instant.parse("2026-11-26T01:00:00Z");

    private GuardianRepository guardians;
    private PetRepository pets;
    private SelectableOptionChecker options;
    private List<Object> published;
    private PetRegistrationService service;

    @BeforeEach
    void setUp() {
        guardians = mock(GuardianRepository.class);
        pets = mock(PetRepository.class);
        options = mock(SelectableOptionChecker.class);
        published = new ArrayList<>();
        ApplicationEventPublisher events = published::add;
        KstClock clock = new KstClock(Clock.fixed(NOW, ZoneOffset.UTC));
        service = new PetRegistrationService(guardians, pets, options, new BirthDateResolver(clock), clock, events);

        AtomicLong ids = new AtomicLong(100);
        when(guardians.save(any(Guardian.class))).thenAnswer(inv -> {
            Guardian g = inv.getArgument(0);
            ReflectionTestUtils.setField(g, "guardianId", 7L);
            return g;
        });
        when(pets.save(any(Pet.class))).thenAnswer(inv -> {
            Pet p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "petId", ids.incrementAndGet());
            return p;
        });
        when(options.isSelectableBreed(anyLong())).thenReturn(true);
        when(options.isSelectableCoatType(anyLong())).thenReturn(true);
    }

    private static RegisterGuardianRequest request(String phone, Boolean privacy, Boolean sms, PetInput pet) {
        return new RegisterGuardianRequest(new GuardianInput("김보호", phone, privacy, sms), pet);
    }

    private static PetInput pet(LocalDate birthDate, PetInput.ApproxAge approx) {
        return new PetInput("보리", birthDate, approx, 1L, 3L);
    }

    @Test
    void 필수만_넣어_등록하면_보호자와_아이를_저장하고_서버가_채우는_값을_넣는다() {
        var result = service.registerGuardian(request("010-0000-0000", null, null, pet(LocalDate.of(2023, 5, 2), null)));

        assertThat(result.guardianId()).isEqualTo(7L);
        assertThat(result.petId()).isEqualTo(101L);

        ArgumentCaptor<Guardian> g = ArgumentCaptor.forClass(Guardian.class);
        verify(guardians).save(g.capture());
        assertThat(g.getValue().getPhone()).isEqualTo("01000000000");
        assertThat(g.getValue().getNameChosung()).isEqualTo("ㄱㅂㅎ");
        assertThat(g.getValue().getPhoneCheckedAt()).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(g.getValue().isPrivacyAgreed()).isFalse();
        assertThat(g.getValue().getPrivacyAgreedAt()).isNull();
        assertThat(g.getValue().isSmsAgreed()).isFalse();
        assertThat(g.getValue().getSmsAgreedAt()).isNull();

        ArgumentCaptor<Pet> p = ArgumentCaptor.forClass(Pet.class);
        verify(pets).save(p.capture());
        assertThat(p.getValue().getGuardianId()).isEqualTo(7L);
        assertThat(p.getValue().getNameChosung()).isEqualTo("ㅂㄹ");
        assertThat(p.getValue().getBirthDate()).isEqualTo(LocalDate.of(2023, 5, 2));
        assertThat(p.getValue().isBirthDateEstimated()).isFalse();
    }

    @Test
    void 동의하면_동의_시간을_한국_시간으로_남긴다() {
        service.registerGuardian(request("01000000000", true, true, pet(LocalDate.of(2023, 5, 2), null)));

        ArgumentCaptor<Guardian> g = ArgumentCaptor.forClass(Guardian.class);
        verify(guardians).save(g.capture());
        assertThat(g.getValue().isPrivacyAgreed()).isTrue();
        assertThat(g.getValue().getPrivacyAgreedAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 10, 0));
        assertThat(g.getValue().isSmsAgreed()).isTrue();
        assertThat(g.getValue().getSmsAgreedAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 10, 0));
    }

    @Test
    void 대략_나이로_등록하면_추정_생년월일() {
        service.registerGuardian(request("01000000000", null, null, pet(null, new PetInput.ApproxAge(null, 3))));

        ArgumentCaptor<Pet> p = ArgumentCaptor.forClass(Pet.class);
        verify(pets).save(p.capture());
        assertThat(p.getValue().getBirthDate()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(p.getValue().isBirthDateEstimated()).isTrue();
    }

    @Test
    void 아이를_저장하면_아이_등록됨_이벤트만_낸다() {
        service.registerGuardian(request("01000000000", null, null, pet(LocalDate.of(2023, 5, 2), null)));
        assertThat(published).containsExactly(new PetRegisteredEvent(101L));
    }

    @Test
    void 선택지에_없는_품종_털_유형_ID는_400이고_저장하지_않는다() {
        when(options.isSelectableBreed(anyLong())).thenReturn(false);
        when(options.isSelectableCoatType(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> service.registerGuardian(
                request("01000000000", null, null, pet(LocalDate.of(2023, 5, 2), null))))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.fields()).extracting(ErrorResponse.FieldError::field)
                            .containsExactly("pet.breedId", "pet.coatTypeId");
                });
        verify(guardians, never()).save(any());
        verify(pets, never()).save(any());
        assertThat(published).isEmpty();
    }

    @Test
    void 숫자가_아니거나_자리수가_틀린_번호는_400() {
        assertThatThrownBy(() -> service.registerGuardian(
                request("010-abcd-0000", null, null, pet(LocalDate.of(2023, 5, 2), null))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.fields())
                        .extracting(ErrorResponse.FieldError::field).containsExactly("guardian.phone"));
        verify(guardians, never()).save(any());
    }

    @Test
    void birthDate와_approxAge를_둘_다_보내면_400이고_저장하지_않는다() {
        assertThatThrownBy(() -> service.registerGuardian(request("01000000000", null, null,
                pet(LocalDate.of(2023, 5, 2), new PetInput.ApproxAge(3, null)))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.fields())
                        .extracting(ErrorResponse.FieldError::field).containsExactly("pet.approxAge"));
        verify(pets, never()).save(any());
    }

    @Test
    void 같은_번호여도_새_보호자로_등록할_수_있다() {
        service.registerGuardian(request("010-0000-0000", null, null, pet(LocalDate.of(2023, 5, 2), null)));
        service.registerGuardian(request("010 0000 0000", null, null, pet(LocalDate.of(2024, 1, 1), null)));
        verify(guardians, org.mockito.Mockito.times(2)).save(any(Guardian.class));
        verify(guardians, never()).findByPhoneOrderByGuardianIdAsc(any());
    }

    @Test
    void 기존_보호자에_아이를_붙이면_보호자는_저장하지_않고_아이만_붙는다() {
        when(guardians.existsById(7L)).thenReturn(true);

        var result = service.addPet(7L, pet(null, new PetInput.ApproxAge(5, null)));

        assertThat(result.petId()).isEqualTo(101L);
        verify(guardians, never()).save(any());
        ArgumentCaptor<Pet> p = ArgumentCaptor.forClass(Pet.class);
        verify(pets).save(p.capture());
        assertThat(p.getValue().getGuardianId()).isEqualTo(7L);
        assertThat(p.getValue().getBirthDate()).isEqualTo(LocalDate.of(2021, 11, 26));
        assertThat(published).containsExactly(new PetRegisteredEvent(101L));
    }

    @Test
    void 없는_보호자에_아이를_붙이면_404() {
        when(guardians.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.addPet(99L, pet(LocalDate.of(2023, 5, 2), null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(pets, never()).save(any());
    }
}
