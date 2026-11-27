package com.dadayiksun.crm.contact.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.dadayiksun.crm.contact.ContactFakes;
import com.dadayiksun.crm.contact.domain.ConversionRule;
import com.dadayiksun.crm.contact.repository.ContactQuery.CompletedSchedule;
import com.dadayiksun.crm.cycle.service.CycleEventListener;
import com.dadayiksun.crm.cycle.service.CycleService;
import com.dadayiksun.crm.visit.domain.VisitRecordedEvent;

/** 재방문 전환 (FR-CNT-004 확인 방법): 예정일 11/20, 누락 기준 14일. 방문 501 */
class ContactConversionListenerTest {

    private static final LocalDate DUE = LocalDate.of(2026, 11, 20);

    private ContactFakes.Query query;
    private ContactFakes.Logs logs;
    private ContactConversionListener listener;

    @BeforeEach
    void setUp() {
        query = new ContactFakes.Query();
        logs = new ContactFakes.Logs();
        listener = new ContactConversionListener(query, logs);
        query.contacts.put(101L, List.of(
                new ConversionRule.Contact(1, LocalDateTime.of(2026, 11, 20, 10, 0)),
                new ConversionRule.Contact(2, LocalDateTime.of(2026, 11, 21, 10, 0))));
    }

    private void visitCompletes(long visitId, LocalDate visitDate) {
        query.completed.put(visitId, new CompletedSchedule(101, DUE, 14, visitDate));
    }

    @Test
    void 기준_14일째_방문이면_방문_직전_마지막_연락을_전환으로() {
        visitCompletes(501, LocalDate.of(2026, 12, 4));

        listener.onVisitRecorded(new VisitRecordedEvent(501));

        assertThat(logs.converted).hasSize(1);
        assertThat(logs.converted.get(0)).containsExactly(2L, 501L);
    }

    @Test
    void 기준_15일째_방문이면_전환이_아니다() {
        visitCompletes(501, LocalDate.of(2026, 12, 5));

        listener.onVisitRecorded(new VisitRecordedEvent(501));

        assertThat(logs.converted).isEmpty();
    }

    @Test
    void 접종_연락_뒤_미용만_받으면_전환이_아니다() {
        // 연락은 접종 주기(101)에만 있고, 이 방문은 미용 주기(102)를 완료했다 (POL-MISS-007)
        query.completed.put(502L, new CompletedSchedule(102, DUE, 14, LocalDate.of(2026, 11, 25)));

        listener.onVisitRecorded(new VisitRecordedEvent(502));

        assertThat(logs.converted).isEmpty();
    }

    @Test
    void 이_방문으로_완료된_주기가_없으면_아무것도_하지_않는다() {
        listener.onVisitRecorded(new VisitRecordedEvent(999));

        assertThat(logs.converted).isEmpty();
    }

    @Test
    void 실제_이벤트에서_cycle이_먼저_주기를_닫고_contact가_뒤에_듣는다() {
        List<String> calls = new ArrayList<>();
        CycleService cycle = mock(CycleService.class);
        doAnswer(inv -> {
            calls.add("cycle");
            return null;
        }).when(cycle).onVisitRecorded(anyLong());
        ContactFakes.Query recording = new ContactFakes.Query() {
            @Override
            public Optional<CompletedSchedule> completedBy(long visitId) {
                calls.add("contact");
                return Optional.empty();
            }
        };
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            // 일부러 contact를 먼저 등록한다: 순서는 등록 순서가 아니라 @Order가 정해야 한다
            ctx.registerBean(ContactConversionListener.class,
                    () -> new ContactConversionListener(recording, new ContactFakes.Logs()));
            ctx.registerBean(CycleEventListener.class, () -> new CycleEventListener(cycle));
            ctx.refresh();
            ctx.publishEvent(new VisitRecordedEvent(501));
        }

        assertThat(calls).containsExactly("cycle", "contact");
    }
}
