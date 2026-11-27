package com.dadayiksun.crm.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.auth.domain.PasswordRule;
import com.dadayiksun.crm.auth.repository.StaffRepository;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;

/**
 * 새 비밀번호 정하기 (FR-ADM-007, POL-ACC-010, API 설계 3-1 {@code POST /api/auth/password}).
 * 임시 비밀번호로 로그인한 직원이 쓴다. 정하면 must_change_password = 0.
 * [?] 지금 비밀번호도 함께 받을지(API 설계 7장 13번)는 정해지지 않아, 계약대로 새 비밀번호만 받는다.
 */
@Service
public class PasswordService {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordService(StaffRepository staffRepository, PasswordEncoder passwordEncoder) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 조건에 안 맞으면 400 PASSWORD_RULE (정책 4-2 '비밀번호 조건'). */
    @Transactional
    public void change(Long staffId, String newPassword) {
        if (!PasswordRule.isValid(newPassword)) {
            throw new ApiException(ErrorCode.PASSWORD_RULE);
        }
        staffRepository.findById(staffId).orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
        staffRepository.updatePassword(staffId, passwordEncoder.encode(newPassword), false);
    }
}
