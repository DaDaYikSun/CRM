package com.dadayiksun.crm.auth.domain;

/**
 * 미용사에게 전체 번호를 가리는 판단 한 곳 (K12, POL-DATA-002, POL-ACC-011, NFR-SEC-003).
 * 전체 번호 API(보호자 상세 · 연락 패널)는 미용사에게 403이고, 그 밖의 응답은 뒤 4자리만 준다.
 * 수정 이력의 {@code broughtByPhone}을 미용사에게 줄 때도 {@link #forRole}을 쓴다.
 */
public final class PhonePolicy {

    private PhonePolicy() {
    }

    public static boolean canSeeFullPhone(Role role) {
        return role == Role.ADMIN || role == Role.DESK;
    }

    /** 전체 번호를 볼 수 있으면 그대로, 아니면 뒤 4자리. */
    public static String forRole(Role role, String phone) {
        if (phone == null || canSeeFullPhone(role)) {
            return phone;
        }
        return phone.length() <= 4 ? phone : phone.substring(phone.length() - 4);
    }
}
