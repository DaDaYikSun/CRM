package com.dadayiksun.crm.view;

import java.util.Collection;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Fake: C4 cycle Reader(#144) 대신 '다음 예정 없음'을 돌려준다 (조회 Fake).
 * #144가 실제 Reader를 내면 이 파일을 지운다 (Bean이 둘이 되면 시작이 실패해 잊지 않는다).
 */
@Component
public class EmptyNextScheduleReader implements NextScheduleReader {

    @Override
    public Map<Long, NextSchedule> nextSchedules(Collection<Long> petIds) {
        return Map.of();
    }
}
