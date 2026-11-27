package com.dadayiksun.crm.contact.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.contact.domain.ContactResult;
import com.dadayiksun.crm.contact.domain.FollowUpRule;
import com.dadayiksun.crm.contact.repository.ContactQuery;
import com.dadayiksun.crm.customer.service.GuardianPhoneStatusService;
import com.dadayiksun.crm.cycle.service.ScheduleContactUpdater;

/**
 * C14 결과별 후속 처리 (FR-CNT-003, POL-TGT-005~009, API 설계 3-6). 연락 결과 저장과 같은 트랜잭션에서 불린다.
 *
 * <ul>
 *   <li>주기의 연락 상태 · 연속 횟수 · 다음 연락 예정일은 cycle 인터페이스로 고친다 (C12, K17 ①). 규칙은 {@link FollowUpRule}</li>
 *   <li>번호 오류면 보호자 번호 상태를 customer 인터페이스로 WRONG (FR-DASH-004 번호 오류 모음)</li>
 *   <li>2일차로 주기를 끝냈고 수신 동의가 있으면 안내 문자 모의 발송 기록을 같은 트랜잭션에 남긴다 (K21).
 *       접종 · 미용이 섞이면 접종 문구 (T4, {@link MockSmsWriter}). 동의가 없으면 문자 없이 끝낸다 (POL-TGT-006)</li>
 *   <li>{@code SmsSender}는 여기서 부르지 않는다. 보낼 문자를 돌려주면 커밋 뒤 {@link ContactSmsDispatcher}가 보낸다</li>
 * </ul>
 */
@Service
public class ContactFollowUpService {

    private final ContactQuery query;
    private final ScheduleContactUpdater schedules;
    private final GuardianPhoneStatusService guardians;
    private final MockSmsWriter sms;

    public ContactFollowUpService(ContactQuery query, ScheduleContactUpdater schedules,
                                  GuardianPhoneStatusService guardians, MockSmsWriter sms) {
        this.query = query;
        this.schedules = schedules;
        this.guardians = guardians;
        this.sms = sms;
    }

    /** @param pendingSms 보낼 문자. 끝낸 항목이 없거나 수신 동의가 없으면 null */
    public record Outcome(List<Long> closedScheduleIds, MockSmsWriter.PendingSms pendingSms) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome apply(String contactGroupId) {
        List<ContactQuery.GroupItem> items = query.groupItems(contactGroupId);
        List<ContactQuery.GroupItem> closed = new ArrayList<>();
        boolean wrongNumber = false;
        for (ContactQuery.GroupItem item : items) {
            ContactResult result = ContactResult.valueOf(item.result());
            FollowUpRule.ScheduleContact next = FollowUpRule.after(result,
                    new FollowUpRule.Prev(item.prevContactState(), item.noAnswerCount(), item.prevNextContactDate()),
                    item.nextContactDate(), item.contactedAt().toLocalDate());
            schedules.update(item.scheduleId(), next.contactState(), next.noAnswerCount(), next.nextContactDate());
            if (next.closed()) {
                closed.add(item);
            }
            wrongNumber |= result == ContactResult.WRONG_NUMBER;
        }
        if (wrongNumber) {
            guardians.markWrong(items.get(0).guardianId());
        }
        List<Long> closedIds = closed.stream().map(ContactQuery.GroupItem::scheduleId).toList();
        if (closed.isEmpty() || !closed.get(0).smsAgreed()) {
            return new Outcome(closedIds, null);
        }
        // 문자는 보호자에게 한 통 (T4). 기록 시각 · 직원은 연락 결과 행과 같게
        List<ContactQuery.SmsTarget> targets = closed.stream()
                .map(i -> new ContactQuery.SmsTarget(i.scheduleId(), i.petId(), i.petName(), i.guardianId(), i.phone(),
                        i.smsAgreed(), true, i.serviceType(), i.vaccineTypeName(), i.majorVaccine(), i.dueDate()))
                .toList();
        ContactQuery.GroupItem first = closed.get(0);
        return new Outcome(closedIds, sms.write(contactGroupId, targets, first.contactedAt(), first.createdBy()));
    }
}
