package com.dadayiksun.crm.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;

/** K16 ⑥, API 설계 1-3. */
class PageQueryTest {

    @Test
    void 없으면_1페이지() {
        assertThat(PageQuery.parse(null).page()).isEqualTo(1);
        assertThat(PageQuery.parse("").page()).isEqualTo(1);
    }

    @Test
    void 한_페이지는_20개() {
        PageQuery q = PageQuery.parse("3");
        assertThat(q.limit()).isEqualTo(20);
        assertThat(q.offset()).isEqualTo(40);
    }

    @Test
    void 영이나_음수나_문자면_VALIDATION_FAILED() {
        for (String raw : new String[] {"0", "-1", "abc", "1.5"}) {
            assertThatThrownBy(() -> PageQuery.parse(raw))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
    }

    @Test
    void 마지막보다_뒤면_빈_목록() {
        assertThat(PageQuery.parse("3").isBeyond(57)).isFalse();
        assertThat(PageQuery.parse("4").isBeyond(57)).isTrue();
        assertThat(PageQuery.parse("1").isBeyond(0)).isTrue();
    }
}
