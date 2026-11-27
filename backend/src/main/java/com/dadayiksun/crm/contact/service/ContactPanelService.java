package com.dadayiksun.crm.contact.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.contact.domain.ContactResult;
import com.dadayiksun.crm.contact.dto.ContactHistoryItem;
import com.dadayiksun.crm.contact.dto.ContactPanelResponse;
import com.dadayiksun.crm.contact.repository.ContactQuery;

/** 연락 패널 · 지난 연락 (C13, FR-CNT-001). 읽기만 한다 (K17). */
@Service
@Transactional(readOnly = true)
public class ContactPanelService {

    private static final Map<String, String> METHOD = Map.of("PHONE", "전화", "SMS", "문자");
    private static final Map<String, String> TALKED_WITH = Map.of("SELF", "본인", "FAMILY", "가족");

    private final ContactQuery query;
    private final KstClock clock;

    public ContactPanelService(ContactQuery query, KstClock clock) {
        this.query = query;
        this.clock = clock;
    }

    public ContactPanelResponse panel(long guardianId) {
        ContactPanelResponse.Guardian guardian = query.guardian(guardianId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        LocalDate today = clock.today();
        Map<Long, String> petNames = new LinkedHashMap<>();
        Map<Long, List<ContactPanelResponse.Item>> items = new LinkedHashMap<>();
        for (ContactQuery.PanelItem row : query.panelItems(guardianId, clock.weekEnd())) {
            petNames.put(row.petId(), row.petName());
            items.computeIfAbsent(row.petId(), k -> new ArrayList<>()).add(row.item());
        }
        List<ContactPanelResponse.Pet> pets = petNames.entrySet().stream()
                .map(e -> new ContactPanelResponse.Pet(e.getKey(), e.getValue(), items.get(e.getKey())))
                .toList();
        return new ContactPanelResponse(guardian, query.todayContact(guardianId, today).orElse(null), pets);
    }

    /** 최근 순, 20개씩. 마지막보다 큰 페이지는 빈 목록과 total (K16 ⑥) */
    public PageResponse<ContactHistoryItem> history(long guardianId, PageQuery page, long staffId) {
        long total = query.countHistory(guardianId);
        LocalDate today = clock.today();
        List<ContactHistoryItem> items = page.isBeyond(total) ? List.of()
                : query.history(guardianId, page.limit(), page.offset()).stream()
                        .map(r -> toItem(r, staffId, today)).toList();
        return PageResponse.of(items, page, total);
    }

    private static ContactHistoryItem toItem(ContactQuery.HistoryRow r, long staffId, LocalDate today) {
        LocalDate date = r.contactedAt().toLocalDate();
        return new ContactHistoryItem("CONTACT", r.id(), date, summary(r), r.memo(), r.staffName(),
                r.contactedAt().atZone(KstClock.ZONE).toOffsetDateTime(), r.edited(),
                r.createdBy() == staffId && date.equals(today), r.converted(), r.mockSms(), r.petName());
    }

    /** "전화 · 본인 · 전화 안 받음". 모의 발송만 한 행은 결과 대신 '모의 발송' */
    static String summary(ContactQuery.HistoryRow r) {
        String result = r.result() == null ? (r.mockSms() ? "모의 발송" : null)
                : ContactResult.valueOf(r.result()).label();
        // Map.of().get(null)은 예외라 비어 있을 수 있는 칸은 먼저 거른다
        String talkedWith = r.talkedWith() == null ? null : TALKED_WITH.get(r.talkedWith());
        return String.join(" · ", Stream.of(METHOD.get(r.method()), talkedWith, result)
                .filter(Objects::nonNull).toList());
    }
}
