package com.dadayiksun.crm.view;

import java.util.Collection;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Fake: C4 cycle Reader(#144) 대신 '다음 예정 없음'을 돌려준다 (조회 Fake).
 * PR #202(#144)는 {@code NextScheduleReader}를 구현하지 않고 {@code cycle.service.CycleQueryService}만 낸다.
 * 그래서 #202가 머지돼도 이 파일은 저절로 사라지지 않고 Bean 충돌도 나지 않는다: {@code nextSchedule}이 계속 null로 남는다.
 * PR #202 머지 뒤 직접 바꿔야 한다: {@code view}에 {@code CycleQueryService}를 감싸는 {@code NextScheduleReader} 구현 하나를 두고
 * (OPEN 중 예정일이 가장 이른 것, 접종 종류 이름은 C5 선택지에서) 이 파일을 지운다. #148 · #149도 그 구현을 같이 쓴다.
 */
@Component
public class EmptyNextScheduleReader implements NextScheduleReader {

    @Override
    public Map<Long, NextSchedule> nextSchedules(Collection<Long> petIds) {
        return Map.of();
    }
}
