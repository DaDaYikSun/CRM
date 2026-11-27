package com.dadayiksun.crm.contact.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.contact.dto.SendSmsRequest;
import com.dadayiksun.crm.contact.repository.ContactQuery;

/**
 * S-04 [문자 보내기] 모의 발송 기록 (FR-CNT-005, POL-CNT-005, API 설계 3-6 {@code POST /api/contacts/sms}).
 *
 * <ul>
 *   <li>보호자에게 한 통, 기록 한 행 (2일차 문자와 같음, #174 결정). 결과 유형은 남기지 않아 후속 처리(C14)를 바꾸지 않는다</li>
 *   <li>예정은 이 보호자 아이의 진행 중 주기여야 한다. 아니면 400. 무지개다리 아이면 409 PET_RAINBOW</li>
 *   <li>수신 동의가 없으면 409 SMS_NOT_AGREED (POL-REG-010). 밤 9시~아침 8시 경고는 화면만 띄우고 서버는 막지 않는다 (POL-CNT-007)</li>
 *   <li>{@code SmsSender}는 커밋 뒤 {@link ContactSmsDispatcher}가 부른다 (K21). 같은 요청 두 번은 IdempotencyFilter(K15)가 막는다</li>
 * </ul>
 */
@Service
public class ContactSmsService {

    private final ContactQuery query;
    private final MockSmsWriter writer;
    private final KstClock clock;

    public ContactSmsService(ContactQuery query, MockSmsWriter writer, KstClock clock) {
        this.query = query;
        this.writer = writer;
        this.clock = clock;
    }

    /** 남긴 기록과 커밋 뒤 보낼 문자 */
    public record Recorded(String contactGroupId, MockSmsWriter.PendingSms sms) {
    }

    @Transactional
    public Recorded record(SendSmsRequest req, long staffId) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (req.guardianId() == null) {
            errors.add(new ErrorResponse.FieldError("guardianId", ErrorCode.required("보호자")));
        }
        List<SendSmsRequest.Item> items = req.items() == null ? List.of() : req.items();
        if (items.isEmpty()) {
            errors.add(new ErrorResponse.FieldError("items", ErrorCode.required("예정 항목")));
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (int i = 0; i < items.size(); i++) {
            SendSmsRequest.Item item = items.get(i);
            if (item == null || item.scheduleId() == null || !ids.add(item.scheduleId())) {
                errors.add(invalid("items[" + i + "].scheduleId"));
            }
        }
        throwIfAny(errors);

        List<ContactQuery.SmsTarget> targets = query.smsTargets(ids);
        if (targets.size() != ids.size() || targets.stream().anyMatch(t -> t.guardianId() != req.guardianId())) {
            throwIfAny(List.of(invalid("items")));
        }
        if (targets.stream().anyMatch(t -> !t.petActive())) {
            throw new ApiException(ErrorCode.PET_RAINBOW);
        }
        if (!targets.get(0).smsAgreed()) {
            throw new ApiException(ErrorCode.SMS_NOT_AGREED);
        }
        String groupId = UUID.randomUUID().toString();
        return new Recorded(groupId, writer.write(groupId, targets, clock.now(), staffId));
    }

    private static ErrorResponse.FieldError invalid(String field) {
        return new ErrorResponse.FieldError(field, ErrorCode.VALIDATION_FALLBACK_MESSAGE);
    }

    private static void throwIfAny(List<ErrorResponse.FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, errors.get(0).message(), List.copyOf(errors));
        }
    }
}
