package com.dadayiksun.crm.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.BirthDate;
import com.dadayiksun.crm.customer.domain.Pet;
import com.dadayiksun.crm.customer.domain.PetProfileChangedEvent;
import com.dadayiksun.crm.customer.domain.PetStatus;
import com.dadayiksun.crm.customer.dto.PetProfilePatch;
import com.dadayiksun.crm.customer.repository.PetRepository;

/** 아이 프로필 수정과 무지개다리 되돌리기 (FR-CUS-008, POL-REG-004 · 009, POL-END-004). 오늘은 2026-11-26. */
class PetProfileAndRevertTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    private PetRepository repo;
    private SelectableOptionChecker options;
    private ChangeLogWriter log;
    private List<Object> events;
    private PetProfileService profile;
    private RainbowRevertService revert;
    private Map<Long, Pet> pets;

    @BeforeEach
    void setUp() {
        repo = mock(PetRepository.class);
        options = mock(SelectableOptionChecker.class);
        log = mock(ChangeLogWriter.class);
        events = new ArrayList<>();
        KstClock clock = new KstClock(Clock.fixed(Instant.parse("2026-11-26T01:00:00Z"), ZoneOffset.UTC));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(String.class), anyLong())).thenReturn("이름");
        profile = new PetProfileService(repo, options, jdbc, clock, events::add);
        revert = new RainbowRevertService(repo, log, clock, events::add);
        pets = new java.util.LinkedHashMap<>();
        when(options.isSelectableBreed(anyLong())).thenReturn(true);
        when(options.isSelectableCoatType(anyLong())).thenReturn(true);
        when(repo.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(pets.get(i.<Long>getArgument(0))));
        when(repo.findGuardianId(anyLong()))
                .thenAnswer(i -> pets.containsKey(i.<Long>getArgument(0)) ? Optional.of(7L) : Optional.empty());
        when(repo.lockByGuardianId(7L)).thenAnswer(i -> List.copyOf(pets.values()));
    }

    private Pet pet(long id, boolean rainbow, LocalDate holdUntil, Long holdBy) {
        Pet p = Pet.register(7L, "아이" + id, 1L, 3L, new BirthDate(LocalDate.of(2023, 5, 2), true));
        ReflectionTestUtils.setField(p, "petId", id);
        if (rainbow) {
            ReflectionTestUtils.setField(p, "status", PetStatus.RAINBOW);
            ReflectionTestUtils.setField(p, "rainbowAt", LocalDateTime.of(2026, 11, 1, 10, 0));
        }
        p.holdContact(holdUntil, holdBy);
        pets.put(id, p);
        return p;
    }

    // ---- 프로필 수정 ----

    @Test
    void 추정_생년월일을_실제_날짜로_고치면_추정이_떨어지고_이벤트가_난다() {
        Pet p = pet(1, false, null, null);

        profile.update(1L, new PetProfilePatch(null, LocalDate.of(2023, 6, 1), null, null, null));

        assertThat(p.isBirthDateEstimated()).isFalse();
        assertThat(events).containsExactly(new PetProfileChangedEvent(1L));
    }

    @Test
    void 미래_생년월일은_400이고_오늘은_받는다() {
        Pet p = pet(1, false, null, null);

        assertThatThrownBy(() -> profile.update(1L, new PetProfilePatch(null, TODAY.plusDays(1), null, null, null)))
                .isInstanceOf(ApiException.class);
        assertThat(p.getBirthDate()).isEqualTo(LocalDate.of(2023, 5, 2));

        profile.update(1L, new PetProfilePatch(null, TODAY, null, null, null));
        assertThat(p.getBirthDate()).isEqualTo(TODAY);
    }

    @Test
    void 털_유형을_바꾸면_이벤트가_나고_이름과_체중만_바꾸면_이벤트가_없다() {
        Pet p = pet(1, false, null, null);

        profile.update(1L, new PetProfilePatch("보리", null, null, null, new BigDecimal("3.4")));
        assertThat(events).isEmpty();
        assertThat(p.getWeightKg()).isEqualByComparingTo("3.4");

        profile.update(1L, new PetProfilePatch(null, null, null, 4L, null));
        assertThat(p.getCoatTypeId()).isEqualTo(4L);
        assertThat(events).hasSize(1);
    }

    @Test
    void 빈_이름_잘못된_체중_고를_수_없는_털_유형은_거부() {
        Pet p = pet(1, false, null, null);
        when(options.isSelectableCoatType(9L)).thenReturn(false);

        assertThatThrownBy(() -> profile.update(1L, new PetProfilePatch(" ", null, null, null, null)))
                .hasMessage("아이 이름을(를) 입력해 주세요.");
        assertThatThrownBy(() -> profile.update(1L, new PetProfilePatch(null, null, null, null, new BigDecimal("0"))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> profile.update(1L, new PetProfilePatch(null, null, null, null, new BigDecimal("3.45"))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> profile.update(1L, new PetProfilePatch(null, null, null, 9L, null)))
                .isInstanceOf(ApiException.class);
        assertThat(p.getCoatTypeId()).isEqualTo(3L);
        assertThat(events).isEmpty();
    }

    @Test
    void 없는_아이는_404() {
        assertThatThrownBy(() -> profile.update(99L, new PetProfilePatch(null, null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ---- 되돌리기 ----

    @Test
    void 아이_하나가_무지개다리면_되돌리면_같은_집_보류가_풀린다() {
        Pet rainbow = pet(1, true, null, null);
        Pet sibling = pet(2, false, TODAY.plusDays(20), 1L);

        var result = revert.revert(1L);

        assertThat(result.releasedPetIds()).containsExactly(2L);
        assertThat(rainbow.isRainbow()).isFalse();
        assertThat(sibling.getContactHoldUntil()).isNull();
        verify(log).write("STATUS_CHANGE", "PET", 1L, Map.of("status", "RAINBOW"), Map.of("status", "ACTIVE"));
        assertThat(events).containsExactly(new PetProfileChangedEvent(1L));
    }

    @Test
    void 무지개다리_아이가_둘이면_남은_아이의_저장된_마감까지_보류가_남는다() {
        Pet a = pet(1, true, null, null);
        Pet b = pet(2, true, TODAY.plusDays(10), 1L);
        Pet c = pet(3, false, TODAY.plusDays(25), 1L);
        // 1번이 먼저, 2번이 나중에 무지개다리가 된 집: 3번 보류는 1번이 걸었고, 2번의 저장된 마감은 오늘 + 15일
        when(log.latestRainbowHoldUntil(2L)).thenReturn(Optional.of(TODAY.plusDays(15)));

        var result = revert.revert(1L);

        assertThat(result.releasedPetIds()).isEmpty();
        assertThat(c.getContactHoldUntil()).isEqualTo(TODAY.plusDays(15));
        assertThat(c.getContactHoldByPetId()).isEqualTo(2L);
        assertThat(a.getContactHoldUntil()).isEqualTo(TODAY.plusDays(15));
        assertThat(a.getContactHoldByPetId()).isEqualTo(2L);
        assertThat(b.getContactHoldUntil()).isNull();
        assertThat(b.isRainbow()).isTrue();
    }

    @Test
    void 남은_아이의_마감이_오늘이면_유지_어제면_이미_끝난_것() {
        pet(1, true, null, null);
        pet(2, true, null, null);
        Pet c = pet(3, false, TODAY.plusDays(5), 1L);
        when(log.latestRainbowHoldUntil(2L)).thenReturn(Optional.of(TODAY));
        revert.revert(1L);
        assertThat(c.getContactHoldUntil()).isEqualTo(TODAY);

        pets.clear();
        pet(1, true, null, null);
        pet(2, true, null, null);
        Pet d = pet(3, false, TODAY.plusDays(5), 1L);
        when(log.latestRainbowHoldUntil(2L)).thenReturn(Optional.of(TODAY.minusDays(1)));
        revert.revert(1L);
        assertThat(d.getContactHoldUntil()).isNull();
    }

    @Test
    void 이미_정상인_아이를_다시_되돌려도_이력과_이벤트가_늘지_않는다() {
        pet(1, false, null, null);

        assertThat(revert.revert(1L).releasedPetIds()).isEmpty();

        verify(log, never()).write(any(), any(), any(), any(), any());
        assertThat(events).isEmpty();
    }

    @Test
    void 없는_아이_되돌리기는_404() {
        assertThatThrownBy(() -> revert.revert(99L)).isInstanceOf(ApiException.class);
    }
}
