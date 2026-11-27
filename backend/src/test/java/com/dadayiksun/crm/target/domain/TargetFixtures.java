package com.dadayiksun.crm.target.domain;

import java.time.LocalDate;

import com.dadayiksun.crm.target.domain.TargetCandidate.ContactState;
import com.dadayiksun.crm.target.domain.TargetCandidate.ServiceType;

/** 테스트용 후보 행. 기본은 연락 대상이 되는 평범한 접종 예정 하나. */
public final class TargetFixtures {

    /** 2026-11-25 수요일. 이번 주 = 11-23(월) ~ 11-29(일) */
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    public static final LocalDate WEEK_END = LocalDate.of(2026, 11, 29);

    private TargetFixtures() {
    }

    public static Builder row(long scheduleId) {
        return new Builder(scheduleId);
    }

    public static final class Builder {
        private final long scheduleId;
        private long petId = 1;
        private String petName = "보리";
        private long guardianId = 1;
        private String guardianName = "김보호";
        private ServiceType service = ServiceType.VACCINE;
        private String vaccineName = "종합백신";
        private int vaccineSort = 1;
        private LocalDate due = TODAY;
        private LocalDate missedAt;
        private ContactState state = ContactState.NONE;
        private LocalDate nextContact;
        private boolean active = true;
        private LocalDate holdUntil;
        private boolean noContact;
        private boolean processed;

        private Builder(long scheduleId) {
            this.scheduleId = scheduleId;
        }

        public Builder pet(long id, String name) { this.petId = id; this.petName = name; return this; }
        public Builder guardian(long id, String name) { this.guardianId = id; this.guardianName = name; return this; }
        public Builder grooming() { this.service = ServiceType.GROOMING; this.vaccineName = null; this.vaccineSort = 0; return this; }
        public Builder vaccine(String name, int sort) { this.service = ServiceType.VACCINE; this.vaccineName = name; this.vaccineSort = sort; return this; }
        public Builder due(LocalDate d) { this.due = d; return this; }
        public Builder missedAt(LocalDate d) { this.missedAt = d; return this; }
        public Builder state(ContactState s) { this.state = s; return this; }
        public Builder nextContact(LocalDate d) { this.nextContact = d; return this; }
        public Builder rainbow() { this.active = false; return this; }
        public Builder holdUntil(LocalDate d) { this.holdUntil = d; return this; }
        public Builder noContact() { this.noContact = true; return this; }
        public Builder processedToday() { this.processed = true; return this; }

        public TargetCandidate build() {
            return new TargetCandidate(scheduleId, petId, petName, guardianId, guardianName, "0000", service,
                    vaccineName, vaccineSort, due, missedAt, state, nextContact, active, holdUntil, noContact, processed);
        }
    }
}
