package com.dadayiksun.crm.customer.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.audit.RuleChangeLogWriter;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.setting.SettingKey;
import com.dadayiksun.crm.common.setting.SettingService;

/**
 * B-03 무지개다리 후 연락 보류 기간 (FR-ADM-004, POL-TGT-003, #175). 주인은 customer (API 설계 3-10, R4).
 * 이미 걸린 보류는 줄이지 않는다: 보류 마감은 무지개다리 처리 때 change_log에 저장해 두고 그 값을 쓴다 (API 설계 7장 17번).
 */
@Service
@Transactional
public class RainbowHoldSettingService {

    // shortcut: 날짜 계산이 넘치지 않게 막는 값일 뿐 정책 값이 아니다. 정책 담당이 범위를 정하면 바꾼다
    static final int MAX_DAYS = 36_500;

    private final SettingService settings;
    private final RuleChangeLogWriter changeLog;

    public RainbowHoldSettingService(SettingService settings, RuleChangeLogWriter changeLog) {
        this.settings = settings;
        this.changeLog = changeLog;
    }

    @Transactional(readOnly = true)
    public int rainbowHoldDays() {
        return settings.getInt(SettingKey.RAINBOW_HOLD_DAYS);
    }

    /** 비었거나 0 이하면 RULE_VALUE_INVALID (FR-ADM-004, POL-ERR-006) */
    public void save(Integer days) {
        if (days == null || days <= 0 || days > MAX_DAYS) {
            throw new ApiException(ErrorCode.RULE_VALUE_INVALID);
        }
        if (changeLog.log("SETTING", SettingKey.RAINBOW_HOLD_DAYS,
                Map.of("value", rainbowHoldDays()), Map.of("value", days))) {
            settings.updateInt(SettingKey.RAINBOW_HOLD_DAYS, days, changeLog.currentStaffId());
        }
    }
}
