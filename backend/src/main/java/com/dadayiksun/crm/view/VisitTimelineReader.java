package com.dadayiksun.crm.view;

import java.util.List;
import java.util.Set;

/**
 * 계약 C9 visit 몫: 아이의 접종 · 미용 방문 줄 (FR-VIS-003, POL-DATA-008). 줄마다 기록한 직원 · 시간이 붙는다.
 * 정렬 · 쪽 나누기는 조립 자리가 한다. [Fake] 실제 구현(#148)이 생기기 전까지 {@link FakeReaders}가 빈 목록을 준다.
 */
public interface VisitTimelineReader {

    /** kinds는 VACCINE · GROOMING만 온다. */
    List<TimelineLine> lines(long petId, Set<TimelineLine.Kind> kinds);
}
