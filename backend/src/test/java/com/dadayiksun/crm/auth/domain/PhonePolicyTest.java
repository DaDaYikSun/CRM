package com.dadayiksun.crm.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhonePolicyTest {

    @Test
    void 미용사에게는_뒤_4자리만_준다() {
        assertThat(PhonePolicy.canSeeFullPhone(Role.GROOMER)).isFalse();
        assertThat(PhonePolicy.forRole(Role.GROOMER, "01000000000")).isEqualTo("0000");
        assertThat(PhonePolicy.forRole(Role.GROOMER, "010-0000-0000")).isEqualTo("0000");
    }

    @Test
    void 관리자_데스크에게는_전체_번호() {
        assertThat(PhonePolicy.forRole(Role.ADMIN, "01000000000")).isEqualTo("01000000000");
        assertThat(PhonePolicy.forRole(Role.DESK, "01000000000")).isEqualTo("01000000000");
        assertThat(PhonePolicy.forRole(Role.GROOMER, null)).isNull();
    }
}
