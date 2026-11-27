package com.dadayiksun.crm.contact;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.contact.dto.ContactPanelResponse;
import com.dadayiksun.crm.contact.repository.ContactLogRepository;
import com.dadayiksun.crm.contact.repository.ContactQuery;
import com.dadayiksun.crm.contact.service.ContactFollowUpService;

/** 연락 서비스 테스트용 메모리 저장소. 값은 테스트가 채운다. */
public final class ContactFakes {

    private ContactFakes() {
    }

    /** 2026-11-25(수) 10시, 한국 시간 */
    public static KstClock clock() {
        return new KstClock(Clock.fixed(
                ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, KstClock.ZONE).toInstant(), KstClock.ZONE));
    }

    public static class Query implements ContactQuery {
        public ContactPanelResponse.Guardian guardian;
        public final List<PanelItem> panelItems = new ArrayList<>();
        public ContactPanelResponse.TodayContact todayContact;
        public final List<HistoryRow> history = new ArrayList<>();
        public final List<OpenSchedule> open = new ArrayList<>();
        public final List<PetContactRow> petHistory = new ArrayList<>();
        public LocalDate askedWeekEnd;

        @Override
        public Optional<ContactPanelResponse.Guardian> guardian(long guardianId) {
            return Optional.ofNullable(guardian).filter(g -> g.guardianId() == guardianId);
        }

        @Override
        public List<PanelItem> panelItems(long guardianId, LocalDate weekEnd) {
            askedWeekEnd = weekEnd;
            return panelItems;
        }

        @Override
        public Optional<ContactPanelResponse.TodayContact> todayContact(long guardianId, LocalDate today) {
            return Optional.ofNullable(todayContact);
        }

        @Override
        public long countHistory(long guardianId) {
            return history.size();
        }

        @Override
        public List<HistoryRow> history(long guardianId, int limit, long offset) {
            return history.stream().skip(offset).limit(limit).toList();
        }

        @Override
        public List<PetContactRow> petHistory(long petId) {
            return petHistory;
        }

        public final List<GroupItem> groupItems = new ArrayList<>();
        public final java.util.Map<String, String> templates = new java.util.HashMap<>();

        @Override
        public List<GroupItem> groupItems(String contactGroupId) {
            return groupItems;
        }

        @Override
        public String messageTemplate(String templateCode) {
            return templates.get(templateCode);
        }

        @Override
        public List<OpenSchedule> openSchedules(Collection<Long> scheduleIds) {
            return open.stream().filter(s -> scheduleIds.contains(s.scheduleId())).toList();
        }
    }

    public static class Logs implements ContactLogRepository {
        public final List<Row> rows = new ArrayList<>();

        @Override
        public void insert(Row row) {
            rows.add(row);
        }

        public final List<MockSmsRow> mockSms = new ArrayList<>();

        @Override
        public void insertMockSms(MockSmsRow row) {
            mockSms.add(row);
        }
    }

    public static class FollowUp extends ContactFollowUpService {
        public final List<String> applied = new ArrayList<>();

        public FollowUp() {
            super(null, null, null, null, null);
        }

        @Override
        public Outcome apply(String contactGroupId) {
            applied.add(contactGroupId);
            return new Outcome(List.of(), null);
        }
    }
}
