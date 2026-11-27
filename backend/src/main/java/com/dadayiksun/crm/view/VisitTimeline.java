package com.dadayiksun.crm.view;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.dadayiksun.crm.visit.service.VisitService;

/**
 * 계약 C9 visit 몫의 실제 구현 (#148): visit이 주는 방문 줄을 타임라인 줄 모양으로 옮기기만 한다 (K17).
 * shortcut: editable은 늘 false (타임라인 계약에 직원이 없음). 기록 수정(#178)을 붙일 때 계약에 직원을 넘겨 채운다
 */
@Component
public class VisitTimeline implements VisitTimelineReader {

    private final VisitService visits;

    public VisitTimeline(VisitService visits) {
        this.visits = visits;
    }

    @Override
    public List<TimelineLine> lines(long petId, Set<TimelineLine.Kind> kinds) {
        return visits.lines(petId, kinds.stream().map(Enum::name).toList()).stream()
                .map(v -> new TimelineLine(TimelineLine.Kind.valueOf(v.serviceType()), v.id(), v.date(), v.summary(),
                        v.memo(), v.staffName(), v.recordedAt(), v.edited(), false, null, null))
                .toList();
    }
}
