package com.dadayiksun.crm.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.dadayiksun.crm.common.audit.RuleChangeLogWriter;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.customer.dto.OptionAdminDtos.BreedInput;
import com.dadayiksun.crm.customer.dto.OptionAdminDtos.CoatTypeInput;

/** B-03 선택지 검사 (FR-ADM-005, 정책 4-2 '같은 선택지', POL-ERR-006, #182). DB 쓰기 전에 막히는 경우만 본다. */
class OptionAdminServiceTest {

    private JdbcTemplate jdbc;
    private RuleChangeLogWriter changeLog;
    private OptionAdminService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        changeLog = mock(RuleChangeLogWriter.class);
        service = new OptionAdminService(jdbc, changeLog);
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((ApiException) e).code();
    }

    @Test
    void 이름이_비면_VALIDATION_FAILED_이름_입력_문구() {
        assertThatThrownBy(() -> service.createBreed(new BreedInput("  ", null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.getMessage()).isEqualTo(ErrorCode.required("이름"));
                });
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void 이름이_너무_길면_VALIDATION_FAILED() {
        assertThatThrownBy(() -> service.createCoatType(new CoatTypeInput("가".repeat(21), 8, null, null)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 같은_이름의_품종이_있으면_OPTION_DUPLICATE이고_저장하지_않는다() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("말티즈"), any())).thenReturn(1);

        assertThatThrownBy(() -> service.createBreed(new BreedInput("말티즈", null, null, null, null)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.OPTION_DUPLICATE));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(changeLog, never()).log(any(), any(), any(), any());
    }

    @Test
    void 털_유형_추가는_미용_간격이_비었거나_0이면_RULE_VALUE_INVALID() {
        for (Integer bad : new Integer[] {null, 0, 128}) {
            assertThatThrownBy(() -> service.createCoatType(new CoatTypeInput("새 털", bad, null, null)))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RULE_VALUE_INVALID));
        }
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void 순서가_음수면_VALIDATION_FAILED() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.createBreed(new BreedInput("비숑", null, null, -1, null)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
