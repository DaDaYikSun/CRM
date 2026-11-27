package com.dadayiksun.crm.auth.access;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.session.FindByIndexNameSessionRepository;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.common.audit.SessionKeys;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 로그인 세션에 넣고 꺼내는 한 곳 (K2 · K19). 세션에는 직원 ID({@link SessionKeys#STAFF_ID}, 감사 컬럼이 읽음)와 역할만 둔다.
 * 비밀번호 · 토큰은 두지 않는다.
 * 직원 ID는 Spring Session의 주인 이름 칸에도 넣는다. 비활성화 · 역할 변경 · 비밀번호 초기화 때 그 직원의 세션을 모두 지우기 위해서다
 * (POL-ACC-004 · 010, 11/25 회의 R5, #150 {@code StaffSessionTerminator}).
 * 로그인 시각도 둔다: 세션 저장소의 만료는 '마지막 요청 뒤'라서, '로그인 뒤 8시간'(POL-ACC-005)은 요청마다 여기서 따로 본다 (API 설계 1-6, #167).
 * 비밀번호 표식도 둔다: 로그인할 때 맞춰 본 비밀번호 해시의 SHA-256. 해시 자체는 두지 않는다.
 * 비밀번호가 바뀌면(초기화 · 새 비밀번호) 직원 행의 해시와 달라져, 세션 지우기가 놓친 세션 —
 * 비밀번호 확인과 세션 저장 사이에 초기화가 끼어든 세션 — 도 다음 요청에서 막힌다 (POL-ACC-010, #183 리뷰).
 */
public final class AuthSession {

    public static final String ROLE = "CRM_STAFF_ROLE";
    /** 임시 비밀번호로 로그인해 새 비밀번호를 정해야 하는 동안 true (POL-ACC-010, #150). */
    public static final String MUST_CHANGE_PASSWORD = "CRM_MUST_CHANGE_PASSWORD";
    /** 로그인한 시각 (epoch 밀리초) */
    public static final String LOGIN_AT = "CRM_LOGIN_AT";
    /** 로그인 뒤 이만큼 지나면 요청이 있어도 늘리지 않고 끝낸다 (POL-ACC-005) */
    public static final Duration MAX_AGE = Duration.ofHours(8);
    /** 로그인할 때의 비밀번호 표식 ({@link #passwordStamp}) */
    public static final String PASSWORD_STAMP = "CRM_PASSWORD_STAMP";

    private AuthSession() {
    }

    /** 로그인 성공. 이미 세션이 있으면 번호를 바꿔 세션 고정 공격을 막는다. */
    public static void start(HttpServletRequest request, Staff staff) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.STAFF_ID, staff.staffId());
        session.setAttribute(ROLE, staff.role().name());
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName(staff.staffId()));
        session.setAttribute(MUST_CHANGE_PASSWORD, staff.mustChangePassword());
        session.setAttribute(LOGIN_AT, Instant.now().toEpochMilli());
        setPasswordStamp(session, staff.passwordHash());
    }

    /** 세션 주인 이름 = 직원 ID 문자열. 세션을 직원 단위로 찾을 때 쓴다. */
    public static String principalName(Long staffId) {
        return String.valueOf(staffId);
    }

    /** 새 비밀번호를 정해야 하는 세션인가. 값이 없으면 false. */
    public static boolean mustChangePassword(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(MUST_CHANGE_PASSWORD));
    }

    /**
     * 새 비밀번호를 정했다: 다른 API를 다시 쓸 수 있다. 이 세션의 비밀번호 표식도 새 해시로 바꾼다
     * (같은 직원의 다른 세션은 표식이 달라 다음 요청에서 로그아웃된다).
     */
    public static void passwordChanged(HttpServletRequest request, String newPasswordHash) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(MUST_CHANGE_PASSWORD, false);
            setPasswordStamp(session, newPasswordHash);
        }
    }

    /**
     * 이 세션이 로그인할 때의 비밀번호가 지금 직원 행의 비밀번호와 같은가.
     * 표식이 없는 세션(이 기능 전에 만든 세션)은 다르다고 본다: 배포 때 한 번 다시 로그인한다
     * (로그인 시각이 없는 세션을 지난 것으로 본 #167과 같음, #183 리뷰).
     */
    public static boolean passwordStampMatches(HttpServletRequest request, String currentPasswordHash) {
        String stamp = passwordStampOf(request);
        return stamp != null && currentPasswordHash != null && MessageDigest.isEqual(
                stamp.getBytes(StandardCharsets.US_ASCII),
                passwordStamp(currentPasswordHash).getBytes(StandardCharsets.US_ASCII));
    }

    /** 이 세션의 비밀번호 표식. 없으면 null. */
    public static String passwordStampOf(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(PASSWORD_STAMP) instanceof String stamp ? stamp : null;
    }

    /** 비밀번호 해시의 SHA-256 (16진수). 세션에 해시를 그대로 두지 않으려고 쓴다. */
    public static String passwordStamp(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setPasswordStamp(HttpSession session, String passwordHash) {
        if (passwordHash == null) {
            session.removeAttribute(PASSWORD_STAMP);
        } else {
            session.setAttribute(PASSWORD_STAMP, passwordStamp(passwordHash));
        }
    }

    /** 로그인 세션을 지운다 (로그아웃 POL-ACC-008 · 막힌 세션). 세션이 없으면 아무것도 안 한다 */
    public static void end(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /** 로그인한 직원. 로그인이 없거나 로그인 뒤 8시간이 지났으면 비어 있다 (지난 세션은 이때 지운다). */
    public static Optional<CurrentStaff> current(HttpServletRequest request) {
        return current(request, Instant.now());
    }

    static Optional<CurrentStaff> current(HttpServletRequest request, Instant now) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object id = session.getAttribute(SessionKeys.STAFF_ID);
        Object role = session.getAttribute(ROLE);
        if (!(id instanceof Number n && role instanceof String r)) {
            return Optional.empty();
        }
        // 로그인 시각이 없는 세션(#167 전에 만든 세션)도 지난 것으로 본다
        if (!(session.getAttribute(LOGIN_AT) instanceof Number loginAt)
                || !now.isBefore(Instant.ofEpochMilli(loginAt.longValue()).plus(MAX_AGE))) {
            session.invalidate();
            return Optional.empty();
        }
        return Optional.of(new CurrentStaff(n.longValue(), Role.valueOf(r)));
    }
}
