package com.dadayiksun.crm.view;

import java.util.List;

/**
 * 계약 C9 contact 몫: 아이의 연락 줄 (FR-VIS-003, POL-DATA-008). 전환 여부는 저장된 {@code is_converted}만 준다 (K17).
 * 실제 구현: {@link ContactTimeline} (#153).
 */
public interface ContactTimelineReader {

    List<TimelineLine> lines(long petId);
}
