package com.dadayiksun.crm.customer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.common.response.ErrorResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.BirthDate;
import com.dadayiksun.crm.customer.dto.PetInput;

/** 대략 나이 → 한국 시간 오늘에서 뺀 날 + '추정' (POL-REG-004, FR-CUS-001 확인 방법). */
class BirthDateResolverTest {

    /** UTC 2026-11-25 15:30 = 한국 시간 2026-11-26 00:30. UTC로 계산하면 하루 전 날짜가 나온다. */
    private static final Instant KST_JUST_AFTER_MIDNIGHT = Instant.parse("2026-11-25T15:30:00Z");

    private BirthDateResolver resolver;
    private List<ErrorResponse.FieldError> errors;

    @BeforeEach
    void setUp() {
        resolver = new BirthDateResolver(new KstClock(Clock.fixed(KST_JUST_AFTER_MIDNIGHT, ZoneOffset.UTC)));
        errors = new ArrayList<>();
    }

    private static PetInput pet(LocalDate birthDate, PetInput.ApproxAge approx) {
        return new PetInput("보리", birthDate, approx, 1L, 1L);
    }

    @Test
    void 대략_나이_3개월은_한국_오늘에서_3개월_뺀_날_추정() {
        Optional<BirthDate> birth = resolver.resolve(pet(null, new PetInput.ApproxAge(null, 3)), errors);
        assertThat(birth).contains(new BirthDate(LocalDate.of(2026, 8, 26), true));
        assertThat(errors).isEmpty();
    }

    @Test
    void 대략_나이_5살은_한국_오늘에서_5년_뺀_날_추정() {
        Optional<BirthDate> birth = resolver.resolve(pet(null, new PetInput.ApproxAge(5, null)), errors);
        assertThat(birth).contains(new BirthDate(LocalDate.of(2021, 11, 26), true));
    }

    @Test
    void 생년월일을_알면_그대로_추정_아님() {
        Optional<BirthDate> birth = resolver.resolve(pet(LocalDate.of(2023, 5, 2), null), errors);
        assertThat(birth).contains(new BirthDate(LocalDate.of(2023, 5, 2), false));
    }

    @Test
    void 둘_다_없으면_생년월일_필수_문구() {
        assertThat(resolver.resolve(pet(null, null), errors)).isEmpty();
        assertThat(resolver.resolve(pet(null, new PetInput.ApproxAge(null, null)), errors)).isEmpty();
        assertThat(errors).extracting(ErrorResponse.FieldError::field).containsOnly("pet.birthDate");
        assertThat(errors.get(0).message()).isEqualTo("생년월일을(를) 입력해 주세요.");
    }

    @Test
    void birthDate와_approxAge를_둘_다_보내면_형식_틀림() {
        assertThat(resolver.resolve(pet(LocalDate.of(2023, 5, 2), new PetInput.ApproxAge(3, null)), errors)).isEmpty();
        assertThat(errors).extracting(ErrorResponse.FieldError::field).containsExactly("pet.approxAge");
        assertThat(errors.get(0).message()).isEqualTo("입력한 값을 확인해 주세요.");
    }

    @Test
    void 대략_나이에_개월과_나이를_함께_넣으면_형식_틀림() {
        assertThat(resolver.resolve(pet(null, new PetInput.ApproxAge(1, 3)), errors)).isEmpty();
        assertThat(errors).extracting(ErrorResponse.FieldError::field).containsExactly("pet.approxAge");
    }
}
