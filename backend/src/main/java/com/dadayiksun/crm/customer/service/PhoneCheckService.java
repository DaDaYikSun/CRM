package com.dadayiksun.crm.customer.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;
import com.dadayiksun.crm.customer.domain.Guardian;
import com.dadayiksun.crm.customer.domain.PhoneNumber;
import com.dadayiksun.crm.customer.dto.PhoneCheckResponse;
import com.dadayiksun.crm.customer.repository.GuardianRepository;
import com.dadayiksun.crm.customer.repository.PetRepository;

/**
 * 계약 C6 같은 전화번호 확인 (API 설계 3-3, FR-CUS-002, POL-REG-005).
 * 같은 번호는 오류가 아니다. 찾은 보호자를 돌려주기만 하고 저장을 막지 않는다 (API 설계 1-5).
 */
@Service
public class PhoneCheckService {

    private final GuardianRepository guardianRepository;
    private final PetRepository petRepository;

    public PhoneCheckService(GuardianRepository guardianRepository, PetRepository petRepository) {
        this.guardianRepository = guardianRepository;
        this.petRepository = petRepository;
    }

    @Transactional(readOnly = true)
    public PhoneCheckResponse check(String rawPhone) {
        String phone = PhoneNumber.normalize(rawPhone);
        if (phone.isEmpty()) {
            throw ApiException.required("phone", "보호자 전화번호");
        }
        if (!PhoneNumber.isStorable(phone)) {
            String message = ErrorCode.VALIDATION_FALLBACK_MESSAGE;
            throw new ApiException(ErrorCode.VALIDATION_FAILED, message,
                    List.of(new ErrorResponse.FieldError("phone", message)));
        }
        List<Guardian> found = guardianRepository.findByPhoneOrderByGuardianIdAsc(phone);
        if (found.isEmpty()) {
            return PhoneCheckResponse.of(List.of());
        }
        Map<Long, Long> petCounts = petRepository
                .countByGuardianIds(found.stream().map(Guardian::getGuardianId).toList()).stream()
                .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), row -> ((Number) row[1]).longValue()));
        return PhoneCheckResponse.of(found.stream()
                .map(g -> new PhoneCheckResponse.Match(g.getGuardianId(), g.getName(),
                        petCounts.getOrDefault(g.getGuardianId(), 0L)))
                .toList());
    }
}
