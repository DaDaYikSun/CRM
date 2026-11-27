package com.dadayiksun.crm.auth.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.auth.domain.PasswordRule;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.auth.domain.StaffAccount;
import com.dadayiksun.crm.auth.repository.StaffRepository;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.time.KstClock;

/**
 * 직원 계정 관리 (FR-ADM-001, POL-ACC-001 · 003 · 004 · 010, 계약 C16).
 * 저장 규칙(아이디 중복, 임시 비밀번호, 해시, 비활성화, 세션 지우기)은 값의 주인인 auth가 가진다.
 * admin 패키지는 화면용 Controller만 둔다 (팀 결정 K14, API 설계 3-10 · 4-2).
 */
@Service
public class StaffAccountService {

    /** 직원 추가 결과. 임시 비밀번호는 이 응답에서 한 번만 돌려준다 (정책 4-4 B-01). DB에는 해시만 있다. */
    public record Created(Long staffId, String tempPassword) {
    }

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffSessionTerminator sessionTerminator;
    private final KstClock clock;

    public StaffAccountService(StaffRepository staffRepository, PasswordEncoder passwordEncoder,
            StaffSessionTerminator sessionTerminator, KstClock clock) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionTerminator = sessionTerminator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StaffAccount> list() {
        return staffRepository.findAllAccounts();
    }

    /** 같은 아이디가 있으면 409 LOGIN_ID_TAKEN (정책 4-2). */
    @Transactional
    public Created create(String loginId, String name, Role role) {
        String id = loginId.trim();
        if (staffRepository.existsByLoginId(id)) {
            throw new ApiException(ErrorCode.LOGIN_ID_TAKEN);
        }
        String tempPassword = PasswordRule.newTemporaryPassword();
        try {
            Long staffId = staffRepository.insert(id, passwordEncoder.encode(tempPassword), name.trim(), role);
            return new Created(staffId, tempPassword);
        } catch (DuplicateKeyException e) { // 동시에 같은 아이디로 추가한 경우
            throw new ApiException(ErrorCode.LOGIN_ID_TAKEN);
        }
    }

    /**
     * 역할 바꾸기 · 비활성화 · 다시 사용 (바꿀 칸만, API 설계 3-10). 없는 직원은 404 NOT_FOUND.
     * 비활성화하거나 역할을 바꾸면 그 직원의 세션을 모두 지운다. 비활성화는 POL-ACC-004 · R5,
     * 역할 변경은 세션에 남은 옛 역할로 계속 쓰지 못하게 하기 위해서다 [제안].
     */
    @Transactional
    public void update(Long staffId, Role role, Boolean active) {
        Staff staff = staffRepository.findById(staffId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        boolean endSessions = false;
        if (role != null && role != staff.role()) {
            staffRepository.updateRole(staffId, role);
            endSessions = true;
        }
        if (active != null && active != staff.active()) {
            if (active) {
                staffRepository.reactivate(staffId);
            } else {
                staffRepository.deactivate(staffId, clock.now());
                endSessions = true;
            }
        }
        if (endSessions) {
            sessionTerminator.terminateAll(staffId);
        }
    }
}
