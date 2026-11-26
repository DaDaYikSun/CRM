package com.dadayiksun.crm.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** CLAUDE.md 7번: 서버 0~9시(UTC 기준 전날)에도 한국 날짜가 나와야 한다. */
class KstClockTest {

    private static KstClock at(String utcInstant) {
        return new KstClock(Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC));
    }

    @Test
    void UTC로_전날이어도_한국_날짜() {
        // UTC 2026-11-25 16:00 = 한국 2026-11-26 01:00
        assertThat(at("2026-11-25T16:00:00Z").today()).isEqualTo(LocalDate.of(2026, 11, 26));
    }

    @Test
    void 이번_주는_월요일부터_일요일() {
        // 한국 2026-11-26 (목)
        KstClock clock = at("2026-11-26T03:00:00Z");
        assertThat(clock.weekStart()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(clock.weekEnd()).isEqualTo(LocalDate.of(2026, 11, 29));
    }

    @Test
    void 일요일_밤도_같은_주() {
        // 한국 2026-11-29 (일) 23:30 = UTC 14:30
        KstClock clock = at("2026-11-29T14:30:00Z");
        assertThat(clock.weekStart()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(clock.weekEnd()).isEqualTo(LocalDate.of(2026, 11, 29));
    }

    @Test
    void 시간에는_09_00_오프셋() {
        assertThat(at("2026-11-26T03:00:00Z").nowWithOffset().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    }
}
