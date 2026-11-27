package com.dadayiksun.crm.customer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.customer.repository.GuardianRepository;

/**
 * C12: contact가 {@code guardian.phone_status}를 고칠 때 쓰는 customer 인터페이스 (K17 ①, API 설계 3-6).
 * 연락 결과 '번호 오류'를 저장하는 트랜잭션 안에서만 불린다. 대시보드 번호 오류 모음(FR-DASH-004)이 이 값을 읽는다.
 */
@Service
public class GuardianPhoneStatusService {

    private final GuardianRepository guardians;

    public GuardianPhoneStatusService(GuardianRepository guardians) {
        this.guardians = guardians;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markWrong(long guardianId) {
        guardians.findById(guardianId)
                .orElseThrow(() -> new IllegalStateException("보호자가 없다: " + guardianId))
                .markPhoneWrong();
    }
}
