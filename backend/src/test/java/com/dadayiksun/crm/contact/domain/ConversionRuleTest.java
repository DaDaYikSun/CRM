package com.dadayiksun.crm.contact.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 재방문 전환 (FR-CNT-004, POL-MISS-005~007). 예정일 11/20, 누락 기준 14일 → 12/4까지 전환 */
class ConversionRuleTest {

    private static final LocalDate DUE = LocalDate.of(2026, 11, 20);
    private static final ConversionRule.Contact CALL_1 =
            new ConversionRule.Contact(1, LocalDateTime.of(2026, 11, 20, 10, 0));
    private static final ConversionRule.Contact CALL_2 =
            new ConversionRule.Contact(2, LocalDateTime.of(2026, 11, 21, 10, 0));

    @Test
    void 기준_14일째_방문은_전환_15일째는_아니다() {
        assertThat(ConversionRule.convertedContact(List.of(CALL_1), DUE, 14, LocalDate.of(2026, 12, 4))).contains(1L);
        assertThat(ConversionRule.convertedContact(List.of(CALL_1), DUE, 14, LocalDate.of(2026, 12, 5))).isEmpty();
    }

    @Test
    void 연락이_여러_번이면_방문_직전_마지막_연락만() {
        assertThat(ConversionRule.convertedContact(List.of(CALL_1, CALL_2), DUE, 14, LocalDate.of(2026, 11, 25)))
                .contains(2L);
    }

    @Test
    void 방문_뒤에_남긴_연락은_세지_않는다_방문을_늦게_입력한_경우() {
        assertThat(ConversionRule.convertedContact(List.of(CALL_1, CALL_2), DUE, 14, LocalDate.of(2026, 11, 20)))
                .contains(1L);
    }

    @Test
    void 연락이_없으면_전환_없음() {
        assertThat(ConversionRule.convertedContact(List.of(), DUE, 14, LocalDate.of(2026, 11, 25))).isEmpty();
    }

    @Test
    void 주기에_복사된_누락_기준을_쓴다() {
        assertThat(ConversionRule.convertedContact(List.of(CALL_1), DUE, 7, LocalDate.of(2026, 11, 28))).isEmpty();
        assertThat(ConversionRule.convertedContact(List.of(CALL_1), DUE, 7, LocalDate.of(2026, 11, 27))).contains(1L);
    }
}
