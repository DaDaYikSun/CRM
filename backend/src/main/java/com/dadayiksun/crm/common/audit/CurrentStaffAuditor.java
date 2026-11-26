package com.dadayiksun.crm.common.audit;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 감사 컬럼의 '기록한 직원'. 세션의 {@link SessionKeys#STAFF_ID}를 읽는다.
 * 로그인 전(시드 · 배치)에는 비어 있다 (DB 설계 4-1: 첫 계정은 created_by 없음).
 */
@Component("currentStaffAuditor")
public class CurrentStaffAuditor implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return Optional.empty();
        }
        Object id = attrs.getAttribute(SessionKeys.STAFF_ID, RequestAttributes.SCOPE_SESSION);
        if (id instanceof Number n) {
            return Optional.of(n.longValue());
        }
        return Optional.empty();
    }
}
