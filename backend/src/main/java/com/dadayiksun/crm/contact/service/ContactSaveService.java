package com.dadayiksun.crm.contact.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.contact.domain.ContactResult;
import com.dadayiksun.crm.contact.dto.SaveContactRequest;
import com.dadayiksun.crm.contact.dto.SaveContactResponse;
import com.dadayiksun.crm.contact.repository.ContactLogRepository;
import com.dadayiksun.crm.contact.repository.ContactQuery;

/**
 * 연락 결과 저장 (C13, FR-CNT-002, API 설계 3-6).
 *
 * <ul>
 *   <li>예정 항목마다 한 행, 같은 {@code contact_group_id} (POL-CNT-002). 결과는 5가지만 (NFR-DATA-003)</li>
 *   <li>예정은 이 보호자 아이의 진행 중 주기여야 한다. 아니면 400. 무지개다리 아이면 409 PET_RAINBOW</li>
 *   <li>다음 연락 예정일이 비었고 결과가 '전화 안 받음'이면 내일(한국 시간)로 채운다 (POL-TGT-006)</li>
 *   <li>같은 트랜잭션에서 C14 후속 처리를 부른다. schedule · guardian은 쓰지 않는다 (K17 ①)</li>
 *   <li>같은 요청 두 번은 {@code IdempotencyFilter}(K15)가 막는다. 응답은 트랜잭션 안에서 만든다 (#193)</li>
 * </ul>
 */
@Service
public class ContactSaveService {

    static final int MEMO_MAX = 200;
    private static final Set<String> METHODS = Set.of("PHONE", "SMS");
    private static final Set<String> TALKED_WITH = Set.of("SELF", "FAMILY");

    private final ContactQuery query;
    private final ContactLogRepository logs;
    private final ContactFollowUpService followUp;
    private final KstClock clock;

    public ContactSaveService(ContactQuery query, ContactLogRepository logs, ContactFollowUpService followUp,
                              KstClock clock) {
        this.query = query;
        this.logs = logs;
        this.followUp = followUp;
        this.clock = clock;
    }

    @Transactional
    public SaveContactResponse save(SaveContactRequest req, long staffId) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (req.guardianId() == null) {
            errors.add(required("guardianId", "보호자"));
        }
        if (req.method() == null || req.method().isBlank()) {
            errors.add(required("method", "연락 방법"));
        } else if (!METHODS.contains(req.method())) {
            errors.add(invalid("method"));
        }
        if (req.talkedWith() != null && !TALKED_WITH.contains(req.talkedWith())) {
            errors.add(invalid("talkedWith"));
        }
        String memo = req.memo() == null || req.memo().isBlank() ? null : req.memo().strip();
        if (memo != null && memo.length() > MEMO_MAX) {
            errors.add(invalid("memo"));
        }
        List<SaveContactRequest.Item> items = req.items() == null ? List.of() : req.items();
        if (items.isEmpty()) {
            errors.add(required("items", "연락 결과"));
        }
        Set<Long> seen = new HashSet<>();
        List<ContactResult> results = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            SaveContactRequest.Item item = items.get(i);
            if (item == null || item.scheduleId() == null || !seen.add(item.scheduleId())) {
                errors.add(invalid("items[" + i + "].scheduleId"));
            }
            Optional<ContactResult> result = item == null ? Optional.empty() : ContactResult.parse(item.result());
            if (item == null || item.result() == null) {
                errors.add(required("items[" + i + "].result", "연락 결과"));
            } else if (result.isEmpty()) {
                errors.add(invalid("items[" + i + "].result"));
            }
            results.add(result.orElse(null));
        }
        throwIfAny(errors);

        Map<Long, ContactQuery.OpenSchedule> open = query.openSchedules(seen).stream()
                .collect(Collectors.toMap(ContactQuery.OpenSchedule::scheduleId, Function.identity()));
        boolean allOwned = seen.stream().allMatch(id -> open.containsKey(id)
                && open.get(id).guardianId() == req.guardianId());
        if (!allOwned) {
            throwIfAny(List.of(invalid("items")));
        }
        if (open.values().stream().anyMatch(s -> !s.petActive())) {
            throw new ApiException(ErrorCode.PET_RAINBOW);
        }

        String groupId = UUID.randomUUID().toString();
        LocalDateTime now = clock.now();
        LocalDate tomorrow = now.toLocalDate().plusDays(1); // now와 같은 시각 기준 (자정 경계)
        for (int i = 0; i < items.size(); i++) {
            ContactQuery.OpenSchedule s = open.get(items.get(i).scheduleId());
            ContactResult result = results.get(i);
            LocalDate next = req.nextContactDate() != null ? req.nextContactDate()
                    : result == ContactResult.NO_ANSWER ? tomorrow : null;
            logs.insert(new ContactLogRepository.Row(groupId, req.guardianId(), s.petId(), s.scheduleId(),
                    req.method(), result.name(), req.talkedWith(), memo, next, now, staffId));
        }
        followUp.apply(groupId);
        return new SaveContactResponse(groupId, null);
    }

    private static ErrorResponse.FieldError required(String field, String label) {
        return new ErrorResponse.FieldError(field, ErrorCode.required(label));
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
