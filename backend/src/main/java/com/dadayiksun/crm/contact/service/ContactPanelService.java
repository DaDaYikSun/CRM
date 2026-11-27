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
        return toItem(r, summary(r), r.createdBy() == staffId && date.equals(today));
    }

    private static ContactHistoryItem toItem(ContactQuery.HistoryRow r, String summary, boolean editable) {
        return new ContactHistoryItem("CONTACT", r.id(), r.contactedAt().toLocalDate(), summary, r.memo(),
                r.staffName(), r.contactedAt().atZone(KstClock.ZONE).toOffsetDateTime(), r.edited(), editable,
                r.converted(), r.mockSms(), r.petName(), r.method(), r.talkedWith(), r.result(), r.itemName());
    }

    /**
     * 한 아이의 연락 줄 (아이 상세 통합 타임라인, FR-VIS-003). 한 번 연락에 같은 아이의 접종 · 미용을 같이 남기면 줄이 둘이라
     * 요약 앞에 항목 이름을 붙인다: "종합백신 · 전화 · 본인 · 예약함".
     * shortcut: editable은 늘 false (타임라인 계약에 직원이 없음). 기록 수정(#178)을 붙일 때 계약에 직원을 넘겨 채운다
     */
    public List<ContactHistoryItem> petHistory(long petId) {
        return query.petHistory(petId).stream()
                .map(r -> toItem(r, r.itemName() == null ? summary(r) : r.itemName() + " · " + summary(r), false))
                .toList();
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
