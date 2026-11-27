package com.dadayiksun.crm.auth.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.auth.repository.StaffRepository;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.time.KstClock;

/** 아이디 · 비밀번호 확인과 마지막 로그인 기록 (FR-AUTH-001, API 설계 3-1). 세션은 Controller가 만든다. */
@Service
public class LoginService {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final KstClock clock;
    /** 없는 아이디도 비밀번호 비교를 한 번 해서, 응답 시간으로 아이디가 있는지 알아내지 못하게 한다. */
    private final String dummyHash;

    public LoginService(StaffRepository staffRepository, PasswordEncoder passwordEncoder, KstClock clock) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** 없는 아이디 · 틀린 비밀번호 · 비활성 계정은 모두 같은 401 LOGIN_FAILED (POL-ACC-004, API 설계 1-6). */
    @Transactional
    public Staff login(String loginId, String password) {
        Staff staff = staffRepository.findByLoginId(loginId).orElse(null);
        boolean matches = passwordEncoder.matches(password, staff != null ? staff.passwordHash() : dummyHash);
        if (staff == null || !matches || !staff.active()) {
            throw new ApiException(ErrorCode.LOGIN_FAILED);
        }
        staffRepository.updateLastLoginAt(staff.staffId(), clock.now());
        return staff;
    }

    /** 세션의 직원이 아직 있고 활성인지 확인해 돌려준다. 아니면 401 SESSION_EXPIRED. */
    @Transactional(readOnly = true)
    public Staff findActive(Long staffId) {
        return staffRepository.findById(staffId).filter(Staff::active)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
    }
}
