package com.dadayiksun.crm.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.auth.access.AuthSession;
import com.dadayiksun.crm.auth.domain.PasswordRule;
import com.dadayiksun.crm.auth.domain.Staff;
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

    /**
     * 조건에 안 맞으면 400 PASSWORD_RULE (정책 4-2 '비밀번호 조건'). 저장한 새 해시를 돌려준다 (세션 비밀번호 표식용).
     * {@code sessionStamp}는 이 세션이 로그인할 때의 비밀번호 표식({@link AuthSession#passwordStampOf}). 그 뒤 관리자가
     * 초기화해 비밀번호가 바뀌었으면 바꾸지 않고 401 SESSION_EXPIRED: 초기화로 생긴 임시 비밀번호를 덮어쓰지 않는다 (#183 리뷰).
     * 읽은 뒤 저장 전에 초기화가 끼어들어도 조건부 UPDATE가 막는다. 표식이 없으면(테스트) 비교하지 않는다.
     */
    @Transactional
    public String change(Long staffId, String newPassword, String sessionStamp) {
        if (!PasswordRule.isValid(newPassword)) {
            throw new ApiException(ErrorCode.PASSWORD_RULE);
        }
        Staff staff = staffRepository.findById(staffId).orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
        if (sessionStamp != null && !sessionStamp.equals(AuthSession.passwordStamp(staff.passwordHash()))) {
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        String hash = passwordEncoder.encode(newPassword);
        if (!staffRepository.updatePasswordIfCurrent(staffId, staff.passwordHash(), hash, false)) {
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        return hash;
    }
}
