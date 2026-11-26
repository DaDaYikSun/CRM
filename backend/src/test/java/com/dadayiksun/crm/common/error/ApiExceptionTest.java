package com.dadayiksun.crm.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 오류 응답에는 늘 message가 있다 (C1 { error: { code, message, fields? } }). */
class ApiExceptionTest {

    @Test
    void 정해진_문구가_있는_코드는_코드만으로_만든다() {
        assertThat(new ApiException(ErrorCode.NOT_FOUND).getMessage()).isEqualTo(ErrorCode.NOT_FOUND.message());
    }

    @Test
    void 정해진_문구가_없는_코드는_문구_없이_만들_수_없다() {
        assertThatThrownBy(() -> new ApiException(ErrorCode.VALIDATION_FAILED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiException(ErrorCode.INTERNAL_ERROR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiException(ErrorCode.VALIDATION_FAILED, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 문구를_넘기면_만든다() {
        assertThat(new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FALLBACK_MESSAGE).getMessage())
                .isEqualTo(ErrorCode.VALIDATION_FALLBACK_MESSAGE);
    }
}
