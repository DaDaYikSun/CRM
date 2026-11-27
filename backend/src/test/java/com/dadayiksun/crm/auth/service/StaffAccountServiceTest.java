package com.dadayiksun.crm.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.dadayiksun.crm.auth.domain.PasswordRule;
import com.dadayiksun.crm.auth.domain.Role;
import com.dadayiksun.crm.auth.domain.Staff;
import com.dadayiksun.crm.auth.repository.StaffRepository;
import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.time.KstClock;

/** 직원 관리 저장 규칙 (FR-ADM-001, POL-ACC-004 · 010, 11/25 회의 R5). */
class StaffAccountServiceTest {

    private final StaffRepository repo = mock(StaffRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final StaffSessionTerminator terminator = mock(StaffSessionTerminator.class);
    private final KstClock clock = new KstClock(Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneId.of("UTC")));
    private final StaffAccountService service = new StaffAccountService(repo, encoder, terminator, clock);

    private static Staff desk(boolean active) {
        return new Staff(7L, "desk02", "hash", "새 데스크", Role.DESK, active, false);
    }

    @Test
    void 추가하면_임시_비밀번호를_한_번_돌려주고_DB에는_해시만_넣는다() {
        when(repo.existsByLoginId("desk02")).thenReturn(false);
        when(repo.insert(eq("desk02"), anyString(), eq("새 데스크"), eq(Role.DESK))).thenReturn(7L);

        StaffAccountService.Created created = service.create(" desk02 ", "새 데스크", Role.DESK);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repo).insert(eq("desk02"), hash.capture(), eq("새 데스크"), eq(Role.DESK));
        assertThat(created.staffId()).isEqualTo(7L);
        assertThat(PasswordRule.isValid(created.tempPassword())).isTrue();
        assertThat(hash.getValue()).isNotEqualTo(created.tempPassword());
        assertThat(encoder.matches(created.tempPassword(), hash.getValue())).isTrue();
    }

    @Test
    void 같은_아이디면_409_LOGIN_ID_TAKEN() {
        when(repo.existsByLoginId("desk02")).thenReturn(true);
        assertThatThrownBy(() -> service.create("desk02", "새 데스크", Role.DESK))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.LOGIN_ID_TAKEN));
        verify(repo, never()).insert(anyString(), anyString(), anyString(), any());
    }

    @Test
    void 비활성화하면_지우지_않고_세션을_모두_지운다() {
        when(repo.findById(7L)).thenReturn(Optional.of(desk(true)));
        service.update(7L, null, false);
        verify(repo).deactivate(7L, LocalDateTime.of(2026, 11, 27, 9, 0));
        verify(terminator).terminateAll(7L);
    }

    @Test
    void 역할을_바꾸면_옛_역할_세션을_지운다() {
        when(repo.findById(7L)).thenReturn(Optional.of(desk(true)));
        service.update(7L, Role.GROOMER, null);
        verify(repo).updateRole(7L, Role.GROOMER);
        verify(terminator).terminateAll(7L);
    }

    @Test
    void 바뀌는_것이_없으면_세션을_지우지_않는다() {
        when(repo.findById(7L)).thenReturn(Optional.of(desk(true)));
        service.update(7L, Role.DESK, true);
        verify(repo, never()).updateRole(any(), any());
        verify(terminator, never()).terminateAll(any());
    }

    @Test
    void 비활성_직원을_다시_사용으로() {
        when(repo.findById(7L)).thenReturn(Optional.of(desk(false)));
        service.update(7L, null, true);
        verify(repo).reactivate(7L);
        verify(terminator, never()).terminateAll(any());
    }

    @Test
    void 없는_직원은_404() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(99L, null, false))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
