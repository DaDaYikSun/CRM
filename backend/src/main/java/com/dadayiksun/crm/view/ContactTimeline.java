package com.dadayiksun.crm.view;

import java.util.List;

import org.springframework.stereotype.Component;

import com.dadayiksun.crm.contact.service.ContactPanelService;

/** 계약 C9 contact 몫의 실제 구현 (#153): contact가 주는 아이의 연락 줄을 타임라인 줄 모양으로 옮기기만 한다 (K17). */
@Component
public class ContactTimeline implements ContactTimelineReader {

    private final ContactPanelService contacts;

    public ContactTimeline(ContactPanelService contacts) {
        this.contacts = contacts;
    }

    @Override
    public List<TimelineLine> lines(long petId) {
        return contacts.petHistory(petId).stream()
                .map(c -> new TimelineLine(TimelineLine.Kind.CONTACT, c.id(), c.date(), c.summary(), c.memo(),
                        c.staffName(), c.recordedAt(), c.edited(), c.editable(), c.converted(), c.mockSms()))
                .toList();
    }
}
