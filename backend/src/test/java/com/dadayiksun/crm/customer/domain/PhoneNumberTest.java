package com.dadayiksun.crm.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 전화번호는 하이픈 · 띄어쓰기를 지우고 숫자만 저장 · 비교한다 (API 설계 1-4, DB ck_guardian_phone). */
class PhoneNumberTest {

    @Test
    void 하이픈_띄어쓰기를_지운다() {
        assertThat(PhoneNumber.normalize("010-0000-0000")).isEqualTo("01000000000");
        assertThat(PhoneNumber.normalize(" 010 0000-0000 ")).isEqualTo("01000000000");
        assertThat(PhoneNumber.normalize(null)).isEmpty();
    }

    @Test
    void 숫자_9에서_11자리만_저장할_수_있다() {
        assertThat(PhoneNumber.isStorable("01000000000")).isTrue();
        assertThat(PhoneNumber.isStorable("021234567")).isTrue();
        assertThat(PhoneNumber.isStorable("02123456")).isFalse();
        assertThat(PhoneNumber.isStorable("010000000000")).isFalse();
        assertThat(PhoneNumber.isStorable(PhoneNumber.normalize("010-abcd-0000"))).isFalse();
    }
}
