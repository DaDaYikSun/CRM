package com.dadayiksun.crm.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.customer.domain.Guardian;
import com.dadayiksun.crm.customer.dto.PhoneCheckResponse;
import com.dadayiksun.crm.customer.repository.GuardianRepository;
import com.dadayiksun.crm.customer.repository.PetRepository;

/** 같은 전화번호 확인 (FR-CUS-002, POL-REG-005, API 설계 1-4). */
class PhoneCheckServiceTest {

    private GuardianRepository guardians;
    private PetRepository pets;
    private PhoneCheckService service;

    @BeforeEach
    void setUp() {
        guardians = mock(GuardianRepository.class);
        pets = mock(PetRepository.class);
        service = new PhoneCheckService(guardians, pets);
    }

    private static Guardian guardian(long id, String name) {
        Guardian g = Guardian.register(name, "01000000000", false, false, LocalDate.of(2026, 1, 1),
                LocalDateTime.of(2026, 1, 1, 9, 0));
        ReflectionTestUtils.setField(g, "guardianId", id);
        return g;
    }

    @Test
    void 하이픈_띄어쓰기가_섞여도_숫자만으로_찾고_아이_수를_준다() {
        when(guardians.findByPhoneOrderByGuardianIdAsc("01000000000"))
                .thenReturn(List.of(guardian(7, "김보호"), guardian(9, "이보호")));
        when(pets.countByGuardianIds(List.of(7L, 9L))).thenReturn(List.<Object[]>of(new Object[] {7L, 2L}));

        PhoneCheckResponse result = service.check("010 0000-0000");

        assertThat(result.exists()).isTrue();
        assertThat(result.guardians()).containsExactly(
                new PhoneCheckResponse.Match(7L, "김보호", 2),
                new PhoneCheckResponse.Match(9L, "이보호", 0));
    }

    @Test
    void 없는_번호면_exists_false() {
        when(guardians.findByPhoneOrderByGuardianIdAsc("01000000000")).thenReturn(List.of());
        PhoneCheckResponse result = service.check("010-0000-0000");
        assertThat(result.exists()).isFalse();
        assertThat(result.guardians()).isEmpty();
    }

    @Test
    void 빈_번호_틀린_번호는_400이고_찾지_않는다() {
        assertThatThrownBy(() -> service.check(" ")).isInstanceOf(ApiException.class)
                .hasMessage("보호자 전화번호을(를) 입력해 주세요.");
        assertThatThrownBy(() -> service.check("010-12")).isInstanceOf(ApiException.class)
                .hasMessage("입력한 값을 확인해 주세요.");
        verify(guardians, never()).findByPhoneOrderByGuardianIdAsc(any());
    }
}
