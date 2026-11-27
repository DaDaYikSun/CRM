package com.dadayiksun.crm.cycle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.cycle.repository.ScheduleRepository;

/**
 * 누락 판정 작업 (팀 결정 K20, DB 설계 4-10 · 5-1). 매일 0시(한국 시간)에 한 번 돈다.
 * OPEN · missed_at 없음 · 예정일 + 그 주기의 기준 < 오늘인 주기에 원래 판정일(예정일 + 기준 + 1)을 넣는다.
 * 하루를 건너뛰고 돌아도 같은 값이 들어간다. 목록 · 지표는 이 값만 읽는다 (T13).
 */
@Component
public class MissJudgementJob {

    private static final Logger log = LoggerFactory.getLogger(MissJudgementJob.class);

    private final ScheduleRepository schedules;
    private final KstClock clock;

    public MissJudgementJob(ScheduleRepository schedules, KstClock clock) {
        this.schedules = schedules;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public int run() {
        int marked = schedules.markMissed(clock.today());
        log.info("누락 판정: {}건", marked);
        return marked;
    }
}
