package com.dadayiksun.crm.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.auth.repository.StaffRepository;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.time.KstClock;

class LoginServiceTest {

    private final StaffRepository repo = mock(StaffRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final KstClock clock = new KstClock(Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneId.of("UTC")));
    private final LoginService service = new LoginService(repo, encoder, clock);

    private Staff staff(boolean active) {
        return new Staff(2L, "desk", encoder.encode("Passw0rd!"), "데스크", Role.DESK, active, false);
    }

    @Test
    void 맞는_비밀번호면_로그인되고_마지막_로그인_시각을_한국_시간으로_채운다() {
        when(repo.findByLoginId("desk")).thenReturn(Optional.of(staff(true)));
        Staff result = service.login("desk", "Passw0rd!");
        assertThat(result.role()).isEqualTo(Role.DESK);
        verify(repo).updateLastLoginAt(2L, LocalDateTime.of(2026, 11, 27, 9, 0));
    }

    @Test
    void 틀린_비밀번호_없는_아이디_비활성_계정은_모두_같은_LOGIN_FAILED() {
        when(repo.findByLoginId("desk")).thenReturn(Optional.of(staff(true)));
        when(repo.findByLoginId("off")).thenReturn(Optional.of(staff(false)));
        when(repo.findByLoginId("none")).thenReturn(Optional.empty());

        for (String[] c : new String[][] {{"desk", "wrong"}, {"none", "Passw0rd!"}, {"off", "Passw0rd!"}}) {
            assertThatThrownBy(() -> service.login(c[0], c[1]))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.code()).isEqualTo(ErrorCode.LOGIN_FAILED);
                        assertThat(e.getMessage()).isEqualTo("아이디 또는 비밀번호가 맞지 않습니다.");
                    });
        }
        verify(repo, never()).updateLastLoginAt(any(), any());
    }

    @Test
    void 세션의_직원이_비활성이거나_없으면_SESSION_EXPIRED() {
        when(repo.findById(2L)).thenReturn(Optional.of(staff(false)));
        when(repo.findById(3L)).thenReturn(Optional.empty());
        for (long id : new long[] {2L, 3L}) {
            assertThatThrownBy(() -> service.findActive(id))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.SESSION_EXPIRED));
        }
        verify(repo, never()).updateLastLoginAt(eq(2L), any());
    }
}
