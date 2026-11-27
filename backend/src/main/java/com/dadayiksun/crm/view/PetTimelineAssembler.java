package com.dadayiksun.crm.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;
import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.customer.service.PetDetailReader;
import com.dadayiksun.crm.view.TimelineLine.Kind;

/**
 * 통합 타임라인 조립 (API 설계 3-5 · 4-1, FR-VIS-003): visit과 contact 줄을 합쳐 날짜 최근 순으로 20개씩 준다.
 * visit이 contact를 읽으면 방향이 거꾸로라 이 자리에서 두 Reader를 합친다 (K13).
 *
 * <p>미용사(POL-ACC-011, NFR-SEC-002): 역할 규칙은 {@code type=VACCINE|CONTACT}만 막고 ALL · 없음은 열어 둔다.
 * 그래서 여기서 VACCINE · CONTACT는 403으로 다시 막고, ALL · 없음은 미용 줄만 모은다 (접종 · 연락 Reader는 부르지도 않는다).
 */
@Component
public class PetTimelineAssembler {

    private final PetDetailReader customer;
    private final VisitTimelineReader visits;
    private final ContactTimelineReader contacts;

    public PetTimelineAssembler(PetDetailReader customer, VisitTimelineReader visits, ContactTimelineReader contacts) {
        this.customer = customer;
        this.visits = visits;
        this.contacts = contacts;
    }

    public PageResponse<TimelineLine> assemble(CurrentStaff staff, Long petId, String typeRaw, String pageRaw) {
        Set<Kind> kinds = parseType(typeRaw);
        PageQuery page = PageQuery.parse(pageRaw);
        customer.requireExists(petId);

        if (staff.role() == Role.GROOMER) {
            if (kinds.size() == 1 && !kinds.contains(Kind.GROOMING)) {
                throw new ApiException(ErrorCode.FORBIDDEN); // type=VACCINE | CONTACT
            }
            kinds = EnumSet.of(Kind.GROOMING); // type=ALL · 없음
        }

        // shortcut: 아이 한 마리의 기록을 모두 읽어 메모리에서 합친다. 기록이 수천 건이 되면 Reader가 쪽 단위로 읽게 바꾼다
        List<TimelineLine> all = new ArrayList<>();
        Set<Kind> visitKinds = EnumSet.copyOf(kinds);
        visitKinds.remove(Kind.CONTACT);
        if (!visitKinds.isEmpty()) {
            all.addAll(visits.lines(petId, visitKinds));
        }
        if (kinds.contains(Kind.CONTACT)) {
            all.addAll(contacts.lines(petId));
        }
        all.sort(Comparator.comparing(TimelineLine::date, Comparator.reverseOrder())
                .thenComparing(TimelineLine::recordedAt, Comparator.reverseOrder())
                .thenComparing(TimelineLine::id, Comparator.reverseOrder()));

        long total = all.size();
        List<TimelineLine> items = page.isBeyond(total) ? List.of()
                : all.subList((int) page.offset(), (int) Math.min(total, page.offset() + page.limit()));
        return PageResponse.of(items, page, total);
    }

    private static Set<Kind> parseType(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equals(raw.trim().toUpperCase(Locale.ROOT))) {
            return EnumSet.allOf(Kind.class);
        }
        try {
            return EnumSet.of(Kind.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            String message = ErrorCode.VALIDATION_FALLBACK_MESSAGE;
            throw new ApiException(ErrorCode.VALIDATION_FAILED, message,
                    List.of(new ErrorResponse.FieldError("type", message)));
        }
    }
}
