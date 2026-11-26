package com.dadayiksun.crm.customer.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.BirthDate;
import com.dadayiksun.crm.customer.dto.PetInput;

/**
 * 입력의 생년월일 · 대략 나이를 저장할 생년월일로 바꾼다 (POL-REG-004, API 설계 3-3).
 * '오늘'은 한국 시간 ({@link KstClock}).
 *
 * <p>둘 중 하나만 받는다. 둘 다 없으면 필수 칸 빠짐, 둘 다 있거나 대략 나이에 개월 · 나이를 함께 넣으면 형식 틀림.
 * 미래 생년월일 · 0 이하 대략 나이는 기준 문서에 정한 것이 없어 막지 않는다 ([?] #145 보고).
 */
@Component
public class BirthDateResolver {

    static final String BIRTH_DATE_FIELD = "pet.birthDate";
    static final String APPROX_AGE_FIELD = "pet.approxAge";
    static final String BIRTH_DATE_LABEL = "생년월일";

    private final KstClock clock;

    public BirthDateResolver(KstClock clock) {
        this.clock = clock;
    }

    /** 저장할 생년월일. 입력이 틀리면 빈 값을 돌려주고 {@code errors}에 칸 오류를 더한다. */
    public Optional<BirthDate> resolve(PetInput pet, List<ErrorResponse.FieldError> errors) {
        PetInput.ApproxAge approx = pet.approxAge();
        boolean hasApprox = approx != null && !approx.isEmpty();
        if (pet.birthDate() == null && !hasApprox) {
            errors.add(new ErrorResponse.FieldError(BIRTH_DATE_FIELD, ErrorCode.required(BIRTH_DATE_LABEL)));
            return Optional.empty();
        }
        if (pet.birthDate() != null && hasApprox) {
            errors.add(new ErrorResponse.FieldError(APPROX_AGE_FIELD, ErrorCode.VALIDATION_FALLBACK_MESSAGE));
            return Optional.empty();
        }
        if (pet.birthDate() != null) {
            return Optional.of(BirthDate.exact(pet.birthDate()));
        }
        if (approx.years() != null && approx.months() != null) {
            errors.add(new ErrorResponse.FieldError(APPROX_AGE_FIELD, ErrorCode.VALIDATION_FALLBACK_MESSAGE));
            return Optional.empty();
        }
        return Optional.of(approx.years() != null
                ? BirthDate.fromYears(clock.today(), approx.years())
                : BirthDate.fromMonths(clock.today(), approx.months()));
    }
}
