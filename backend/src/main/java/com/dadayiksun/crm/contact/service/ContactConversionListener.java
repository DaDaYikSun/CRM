package com.dadayiksun.crm.contact.service;

import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.dadayiksun.crm.contact.domain.ConversionRule;
import com.dadayiksun.crm.contact.repository.ContactLogRepository;
import com.dadayiksun.crm.contact.repository.ContactQuery;
import com.dadayiksun.crm.visit.domain.VisitRecordedEvent;

/**
 * 재방문 전환 판정 (C14, FR-CNT-004, POL-MISS-005~007, DB 설계 5-3). '방문 기록됨'을 같은 트랜잭션에서 듣는다 (K13).
 *
 * <ul>
 *   <li>cycle({@code CycleEventListener}, {@code @Order(0)})이 그 방문으로 주기를 닫은 <b>뒤</b>에 들어야 해서 순서가 더 뒤다
 *       (API 설계 3-5 '리스너 순서 cycle → contact')</li>
 *   <li>그 방문이 완료시킨 주기의 연락만 보고, 고르는 규칙은 {@link ConversionRule}(더미 적재기와 같이 쓰는 순수 함수)</li>
 *   <li>늦게 입력한 방문처럼 이 방문으로 완료된 주기가 없으면 아무것도 하지 않는다. 같은 방문을 다시 들어도 같은 행을 다시 표시할 뿐이다</li>
 *   <li>여기서 실패하면 방문 저장도 되돌아간다 (같은 트랜잭션, K13). 전환 표시가 빠진 채 방문만 남지 않게 하려는 것</li>
 * </ul>
 */
@Component
public class ContactConversionListener {

    /** cycle 리스너(0)보다 뒤 */
    public static final int ORDER = 1;

    private final ContactQuery query;
    private final ContactLogRepository logs;

    public ContactConversionListener(ContactQuery query, ContactLogRepository logs) {
        this.query = query;
        this.logs = logs;
    }

    @EventListener
    @Order(ORDER)
    public void onVisitRecorded(VisitRecordedEvent event) {
        Optional<ContactQuery.CompletedSchedule> completed = query.completedBy(event.visitId());
        if (completed.isEmpty()) {
            return;
        }
        ContactQuery.CompletedSchedule s = completed.get();
        // 2일차 모의 발송 문자 행도 연락이다: 전화 뒤 마지막으로 한 안내가 문자면 문자 행이 전환된다 (PR #232 리뷰에서 정함)
        Optional<Long> converted = ConversionRule.convertedContact(query.contactsOf(s.scheduleId()), s.dueDate(),
                s.missThresholdDays(), s.visitDate());
        converted.ifPresent(contactLogId -> logs.markConverted(contactLogId, event.visitId()));
    }
}
