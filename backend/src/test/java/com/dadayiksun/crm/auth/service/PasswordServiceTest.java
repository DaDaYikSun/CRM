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

import com.dadayiksun.crm.auth.access.AuthSession;
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
        when(repo.updatePasswordIfCurrent(eq(5L), eq("old"), anyString(), eq(false))).thenReturn(true);
        String saved = service.change(5L, "newPass99", AuthSession.passwordStamp("old"));
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repo).updatePasswordIfCurrent(eq(5L), eq("old"), hash.capture(), eq(false));
        assertThat(encoder.matches("newPass99", hash.getValue())).isTrue();
        assertThat(saved).isEqualTo(hash.getValue()); // 세션 비밀번호 표식용 (#183 리뷰)
    }

    @Test
    void 로그인_뒤_관리자가_초기화했으면_바꾸지_않고_401() {
        // 세션은 옛 비밀번호로 로그인했는데 직원 행은 이미 임시 비밀번호 (#183 리뷰)
        when(repo.findById(5L)).thenReturn(Optional.of(new Staff(5L, "desk02", "temp", "새 데스크", Role.DESK, true, true)));
        assertThatThrownBy(() -> service.change(5L, "newPass99", AuthSession.passwordStamp("old")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.SESSION_EXPIRED));
        verify(repo, never()).updatePasswordIfCurrent(anyLong(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void 읽은_뒤_저장_전에_초기화가_끼어들면_덮어쓰지_않고_401() {
        when(repo.findById(5L)).thenReturn(Optional.of(new Staff(5L, "desk02", "old", "새 데스크", Role.DESK, true, true)));
        when(repo.updatePasswordIfCurrent(eq(5L), eq("old"), anyString(), eq(false))).thenReturn(false);
        assertThatThrownBy(() -> service.change(5L, "newPass99", AuthSession.passwordStamp("old")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.SESSION_EXPIRED));
    }

    @Test
    void 조건에_안_맞으면_400_PASSWORD_RULE() {
        for (String weak : new String[] {"short1", "onlyletters", "12345678"}) {
            assertThatThrownBy(() -> service.change(5L, weak, null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.PASSWORD_RULE));
        }
        verify(repo, never()).updatePasswordIfCurrent(anyLong(), anyString(), anyString(), anyBoolean());
    }
}
