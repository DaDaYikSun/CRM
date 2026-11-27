package com.dadayiksun.crm.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.auth.repository.StaffRepository;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;

/** 새 비밀번호 정하기 (POL-ACC-010). */
class PasswordServiceTest {

    private final StaffRepository repo = mock(StaffRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final PasswordService service = new PasswordService(repo, encoder);

    @Test
    void 조건에_맞으면_해시로_바꾸고_임시_상태를_푼다() {
        when(repo.findById(5L)).thenReturn(Optional.of(new Staff(5L, "desk02", "old", "새 데스크", Role.DESK, true, true)));
        service.change(5L, "newPass99");
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repo).updatePassword(eq(5L), hash.capture(), eq(false));
        assertThat(encoder.matches("newPass99", hash.getValue())).isTrue();
    }

    @Test
    void 조건에_안_맞으면_400_PASSWORD_RULE() {
        for (String weak : new String[] {"short1", "onlyletters", "12345678"}) {
            assertThatThrownBy(() -> service.change(5L, weak))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.PASSWORD_RULE));
        }
        verify(repo, never()).updatePassword(anyLong(), anyString(), anyBoolean());
    }
}
