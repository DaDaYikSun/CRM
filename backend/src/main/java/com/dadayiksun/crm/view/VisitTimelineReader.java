package com.dadayiksun.crm.view;

import java.util.List;
import java.util.Set;

/**
 * 계약 C9 visit 몫: 아이의 접종 · 미용 방문 줄 (FR-VIS-003, POL-DATA-008). 줄마다 기록한 직원 · 시간이 붙는다.
 * 정렬 · 쪽 나누기는 조립 자리가 한다. 실제 구현: {@link VisitTimeline} (#148).
 */
public interface VisitTimelineReader {

    /** kinds는 VACCINE · GROOMING만 온다. */
    List<TimelineLine> lines(long petId, Set<TimelineLine.Kind> kinds);
}
