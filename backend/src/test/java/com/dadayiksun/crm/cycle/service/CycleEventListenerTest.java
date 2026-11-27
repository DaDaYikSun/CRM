package com.dadayiksun.crm.cycle.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.visit.domain.VisitRecordedEvent;

/** '방문 기록됨'(#148) → cycle이 그 방문으로 주기를 닫는다 (K13, PR #220 리뷰) */
class CycleEventListenerTest {

    @Test
    void 방문_기록됨을_들으면_그_방문으로_주기를_처리한다() {
        CycleService cycle = mock(CycleService.class);

        new CycleEventListener(cycle).onVisitRecorded(new VisitRecordedEvent(501));

        verify(cycle).onVisitRecorded(501);
    }
}
