package com.dadayiksun.crm.customer.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.Guardian;
import com.dadayiksun.crm.customer.domain.GuardianTag;
import com.dadayiksun.crm.customer.domain.PhoneNumber;
import com.dadayiksun.crm.customer.repository.GuardianRepository;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@code PATCH /api/guardians/{id}} 보호자 정보 · 주의 태그 수정 (C10, FR-CUS-008, POL-REG-008 · 009, API 설계 3-3).
 *
 * <p>바꿀 칸만 받는다. 칸이 아예 없으면 그대로, 값이 {@code null}이면 비운다 (예: {@code "contactTimeNote": null}).
 * 그래서 입력은 record가 아니라 JSON 그대로({@link JsonNode}) 받는다.
 * 같은 요청을 두 번 보내도 값이 같으면 바뀌는 것이 없어 이력이 더 쌓이지 않는다.
 */
@Service
public class GuardianUpdateService {

    private static final int NAME_MAX = 30;
    private static final int NOTE_MAX = 50;

    private final GuardianRepository guardians;
    private final ChangeLogWriter changeLog;
    private final KstClock clock;

    public GuardianUpdateService(GuardianRepository guardians, ChangeLogWriter changeLog, KstClock clock) {
        this.guardians = guardians;
        this.changeLog = changeLog;
        this.clock = clock;
    }

    @Transactional
    public void update(Long guardianId, JsonNode patch) {
        Guardian g = guardians.findById(guardianId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        List<ErrorResponse.FieldError> errors = new ArrayList<>();

        String name = null;
        if (patch.has("name")) {
            name = text(patch, "name");
            if (name == null || name.isBlank()) {
                throw ApiException.required("name", "보호자 이름");
            }
            name = name.trim();
            if (name.length() > NAME_MAX) {
                errors.add(invalid("name"));
            }
        }
        String phone = null;
        if (patch.has("phone")) {
            phone = PhoneNumber.normalize(text(patch, "phone"));
            if (!PhoneNumber.isStorable(phone)) {
                errors.add(invalid("phone"));
            }
        }
        String subPhone = g.getSubPhone();
        if (patch.has("subPhone")) {
            subPhone = blankToNull(PhoneNumber.normalize(text(patch, "subPhone")));
            if (subPhone != null && !PhoneNumber.isStorable(subPhone)) {
                errors.add(invalid("subPhone"));
            }
        }
        String preferred = g.getPreferredContact();
        if (patch.has("preferredContact")) {
            preferred = blankToNull(text(patch, "preferredContact"));
            if (preferred != null && !preferred.equals("PHONE") && !preferred.equals("SMS")) {
                errors.add(invalid("preferredContact"));
            }
        }
        Set<GuardianTag> before = g.tags();
        Set<GuardianTag> tags = patch.has("tags") ? parseTags(patch.get("tags"), errors) : before;
        String note = g.getContactTimeNote();
        if (patch.has("contactTimeNote")) {
            note = blankToNull(text(patch, "contactTimeNote"));
            // 메모는 '연락 선호 시간' 태그가 있을 때만 받는다 (API 설계 3-3, DB ck_guardian_contact_time)
            if (note != null && !tags.contains(GuardianTag.CONTACT_TIME)) {
                errors.add(invalid("contactTimeNote"));
            }
        }
        if (note != null && note.length() > NOTE_MAX) {
            errors.add(invalid("contactTimeNote"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, errors.get(0).message(), List.copyOf(errors));
        }

        if (name != null && !name.equals(g.getName())) {
            g.rename(name);
        }
        if (phone != null && !phone.equals(g.getPhone())) {
            g.changePhone(phone, clock.today());
        }
        g.changeSubPhone(subPhone);
        g.changePreferredContact(preferred);
        g.changeTags(tags);
        g.changeContactTimeNote(tags.contains(GuardianTag.CONTACT_TIME) ? note : null);
        if (!tags.equals(before)) {
            changeLog.write("TAG_CHANGE", "GUARDIAN", guardianId, Map.of("tags", names(before)),
                    Map.of("tags", names(tags)));
        }
    }

    /** 4가지 코드 밖의 값이나 배열이 아닌 값은 오류 (NFR-DATA-003, POL-DATA-009). */
    private static Set<GuardianTag> parseTags(JsonNode node, List<ErrorResponse.FieldError> errors) {
        Set<GuardianTag> tags = EnumSet.noneOf(GuardianTag.class);
        if (node == null || !node.isArray()) {
            errors.add(invalid("tags"));
            return tags;
        }
        for (JsonNode code : node) {
            try {
                tags.add(GuardianTag.valueOf(code.asText()));
            } catch (IllegalArgumentException e) {
                errors.add(invalid("tags"));
            }
        }
        return tags;
    }

    private static List<String> names(Set<GuardianTag> tags) {
        return tags.stream().map(Enum::name).toList();
    }

    private static String text(JsonNode patch, String field) {
        JsonNode node = patch.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FALLBACK_MESSAGE,
                    List.of(invalid(field)));
        }
        return node.asText();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ErrorResponse.FieldError invalid(String field) {
        return new ErrorResponse.FieldError(field, ErrorCode.VALIDATION_FALLBACK_MESSAGE);
    }
}
