package com.dadayiksun.crm.admin.dto;

import java.time.OffsetDateTime;

import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.StaffAccount;
import com.dadayiksun.crm.common.time.KstClock;

/** GET /api/admin/staff 한 줄 (API 설계 3-10). 시간은 +09:00 (API 설계 1-4). */
public record StaffItem(
        Long staffId,
        String loginId,
        String name,
        Role role,
        boolean isActive,
        OffsetDateTime lastLoginAt,
        OffsetDateTime deactivatedAt) {

    public static StaffItem from(StaffAccount account) {
        return new StaffItem(account.staffId(), account.loginId(), account.name(), account.role(), account.active(),
                account.lastLoginAt() == null ? null : account.lastLoginAt().atZone(KstClock.ZONE).toOffsetDateTime(),
                account.deactivatedAt() == null ? null : account.deactivatedAt().atZone(KstClock.ZONE).toOffsetDateTime());
    }
}
