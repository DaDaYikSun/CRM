package com.dadayiksun.crm.view;

import java.util.List;

/**
 * 계약 C9 contact 몫: 아이의 연락 줄 (FR-VIS-003, POL-DATA-008). 전환 여부는 저장된 {@code is_converted}만 준다 (K17).
 * [Fake] 실제 구현(#153)이 생기기 전까지 {@link FakeReaders}가 빈 목록을 준다.
 */
public interface ContactTimelineReader {

    List<TimelineLine> lines(long petId);
}
