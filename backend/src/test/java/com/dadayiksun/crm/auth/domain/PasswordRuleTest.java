package com.dadayiksun.crm.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** POL-ACC-010: 8자 이상, 영문과 숫자를 섞는다. */
class PasswordRuleTest {

    @Test
    void 조건에_맞는_비밀번호() {
        assertThat(PasswordRule.isValid("abcd1234")).isTrue();
        assertThat(PasswordRule.isValid("ABCDEFG9")).isTrue();
    }

    @Test
    void 조건에_안_맞는_비밀번호() {
        assertThat(PasswordRule.isValid(null)).isFalse();
        assertThat(PasswordRule.isValid("abc123")).isFalse(); // 8자 미만
        assertThat(PasswordRule.isValid("abcdefgh")).isFalse(); // 숫자 없음
        assertThat(PasswordRule.isValid("12345678")).isFalse(); // 영문 없음
    }

    @RepeatedTest(50)
    void 임시_비밀번호도_조건을_지킨다() {
        String temp = PasswordRule.newTemporaryPassword();
        assertThat(PasswordRule.isValid(temp)).isTrue();
        assertThat(temp).hasSize(10);
    }
}
