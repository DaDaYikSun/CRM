package com.dadayiksun.crm.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 저장할 때 이름 초성을 채운다 (DB 설계 4-2 · 4-3 name_chosung, FR-SRCH-003). */
class ChosungTest {

    @Test
    void 한글_이름은_초성으로() {
        assertThat(Chosung.of("김보호")).isEqualTo("ㄱㅂㅎ");
        assertThat(Chosung.of("보리")).isEqualTo("ㅂㄹ");
        assertThat(Chosung.of("뽀삐")).isEqualTo("ㅃㅃ");
        assertThat(Chosung.of("힣가")).isEqualTo("ㅎㄱ");
    }

    @Test
    void 한글_음절이_아닌_글자는_그대로() {
        assertThat(Chosung.of("Coco 2")).isEqualTo("Coco 2");
        assertThat(Chosung.of("ㄱ보리")).isEqualTo("ㄱㅂㄹ");
    }
}
