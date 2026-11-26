package com.dadayiksun.crm.common.time;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import org.springframework.stereotype.Component;

/**
 * 한국 시간 기준 '오늘' · '이번 주(월~일)' (CLAUDE.md 7번, API 설계 1-4, POL-TGT-001).
 * DB의 CURDATE()를 쓰지 않고 이 값을 :today로 넘긴다 (DB 설계 1장).
 * 테스트에서는 {@link Clock}을 바꿔 끼운다.
 */
@Component
public class KstClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public KstClock() {
        this(Clock.system(ZONE));
    }

    public KstClock(Clock clock) {
        this.clock = clock.withZone(ZONE);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** JSON 시간 칸용. 오프셋 +09:00이 붙는다. */
    public OffsetDateTime nowWithOffset() {
        return OffsetDateTime.now(clock);
    }

    /** 이번 주 월요일 (한국 시간). */
    public LocalDate weekStart() {
        return today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 이번 주 일요일 (한국 시간). */
    public LocalDate weekEnd() {
        return today().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }
}
