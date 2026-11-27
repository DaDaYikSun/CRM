package com.dadayiksun.crm.cycle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 앱이 켜질 때 접종 규칙으로 아직 오지 않은 예정일을 한 번 다시 계산한다 (FR-CYC-003, POL-CYC-005, PR #221 리뷰).
 * 규칙 행은 마이그레이션(V4 등)으로도 바뀌는데, 마이그레이션은 이미 있는 예정 일정을 고치지 못한다.
 * 그래서 새 규칙 종류의 첫 예정을 만들고, 바뀐 규칙의 아직 오지 않은 예정일을 고친다.
 * 지난 예정일 · 누락 판정 · 완료 행은 그대로다. 규칙이 그대로면 결과도 그대로라 여러 번 켜도 바뀌는 것이 없다.
 * 더미 적재기보다 먼저 돈다 (빈 DB면 할 일이 없다).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RuleRecalculationOnStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RuleRecalculationOnStartup.class);

    private final CycleService cycle;

    public RuleRecalculationOnStartup(CycleService cycle) {
        this.cycle = cycle;
    }

    @Override
    public void run(ApplicationArguments args) {
        long started = System.currentTimeMillis();
        cycle.recalculateByRule();
        log.info("접종 규칙으로 아직 오지 않은 예정일을 다시 계산했습니다 ({}ms)", System.currentTimeMillis() - started);
    }
}
