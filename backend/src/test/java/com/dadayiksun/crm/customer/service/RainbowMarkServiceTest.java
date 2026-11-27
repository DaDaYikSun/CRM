package com.dadayiksun.crm.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.setting.SettingKey;
import com.dadayiksun.crm.common.setting.SettingService;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.BirthDate;
import com.dadayiksun.crm.customer.domain.Pet;
import com.dadayiksun.crm.customer.domain.PetProfileChangedEvent;
import com.dadayiksun.crm.customer.dto.RainbowResponse;
import com.dadayiksun.crm.customer.repository.PetRepository;

/** 무지개다리로 표시 (FR-CUS-007, POL-END-001 · 003). 오늘은 2026-11-26, 보류 기간은 설정값에서 읽는다. */
class RainbowMarkServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    private PetRepository repo;
    private ChangeLogWriter log;
    private SettingService settings;
    private List<Object> events;
    private RainbowMarkService service;
    private Map<Long, Pet> pets;

    @BeforeEach
    void setUp() {
        repo = mock(PetRepository.class);
        log = mock(ChangeLogWriter.class);
        settings = mock(SettingService.class);
        events = new ArrayList<>();
        KstClock clock = new KstClock(Clock.fixed(Instant.parse("2026-11-26T01:00:00Z"), ZoneOffset.UTC));
        service = new RainbowMarkService(repo, log, settings, clock, events::add);
        pets = new LinkedHashMap<>();
        when(settings.getInt(SettingKey.RAINBOW_HOLD_DAYS)).thenReturn(30);
        when(repo.findGuardianId(anyLong()))
                .thenAnswer(i -> pets.containsKey(i.<Long>getArgument(0)) ? Optional.of(7L) : Optional.empty());
        when(repo.lockByGuardianId(7L)).thenAnswer(i -> List.copyOf(pets.values()));
    }

    private Pet pet(long id) {
        Pet p = Pet.register(7L, "아이" + id, 1L, 3L, new BirthDate(LocalDate.of(2023, 5, 2), true));
        ReflectionTestUtils.setField(p, "petId", id);
        pets.put(id, p);
        return p;
    }

    @Test
    void 같은_집_다른_아이는_설정값만큼_보류되고_이력에_holdUntil이_남는다() {
        Pet a = pet(1);
        Pet b = pet(2);
        Pet c = pet(3);

        RainbowResponse r = service.mark(1L);

        assertThat(a.isRainbow()).isTrue();
        assertThat(r.holdUntil()).isEqualTo(TODAY.plusDays(30));
        assertThat(r.heldPetIds()).containsExactly(2L, 3L);
        assertThat(b.getContactHoldUntil()).isEqualTo(TODAY.plusDays(30));
        assertThat(c.getContactHoldByPetId()).isEqualTo(1L);
        assertThat(a.getContactHoldUntil()).isNull();
        // 되돌리기가 읽는 모양 그대로 (holdUntil은 문자열)
        verify(log).write("STATUS_CHANGE", "PET", 1L, Map.of("status", "ACTIVE"),
                Map.of("status", "RAINBOW", "holdUntil", "2026-12-26"));
        assertThat(events).containsExactly(new PetProfileChangedEvent(1L));
        // cycle이 JDBC로 읽기 전에 저장돼 있어야 한다
        verify(repo).flush();
    }

    @Test
    void 보류_기간은_코드가_아니라_설정값을_따른다() {
        pet(1);
        Pet b = pet(2);
        when(settings.getInt(SettingKey.RAINBOW_HOLD_DAYS)).thenReturn(10);

        service.mark(1L);

        assertThat(b.getContactHoldUntil()).isEqualTo(TODAY.plusDays(10));
    }

    @Test
    void 혼자인_아이는_보류_대상이_없다() {
        pet(1);

        RainbowResponse r = service.mark(1L);

        assertThat(r.heldPetIds()).isEmpty();
        assertThat(r.holdUntil()).isEqualTo(TODAY.plusDays(30));
    }

    @Test
    void 이미_더_늦은_보류가_있으면_지키고_이른_보류는_늦은_쪽으로() {
        pet(1);
        Pet late = pet(2);
        late.holdContact(TODAY.plusDays(40), 9L);
        Pet early = pet(3);
        early.holdContact(TODAY.plusDays(5), 9L);

        service.mark(1L);

        assertThat(late.getContactHoldUntil()).isEqualTo(TODAY.plusDays(40));
        assertThat(late.getContactHoldByPetId()).isEqualTo(9L);
        assertThat(early.getContactHoldUntil()).isEqualTo(TODAY.plusDays(30));
        assertThat(early.getContactHoldByPetId()).isEqualTo(1L);
    }

    @Test
    void 이미_무지개다리인_다른_아이에게는_보류를_걸지_않는다() {
        pet(1);
        Pet gone = pet(2);
        gone.markRainbow(TODAY.atStartOfDay());

        RainbowResponse r = service.mark(1L);

        assertThat(gone.getContactHoldUntil()).isNull();
        assertThat(r.heldPetIds()).isEmpty();
    }

    @Test
    void 이미_무지개다리인_아이를_다시_표시하면_아무것도_하지_않는다() {
        Pet a = pet(1);
        Pet b = pet(2);
        a.markRainbow(TODAY.atStartOfDay());
        when(log.latestRainbowHoldUntil(1L)).thenReturn(Optional.of(TODAY.plusDays(3)));

        RainbowResponse r = service.mark(1L);

        assertThat(r.holdUntil()).isEqualTo(TODAY.plusDays(3));
        assertThat(r.heldPetIds()).isEmpty();
        assertThat(b.getContactHoldUntil()).isNull();
        verify(log, never()).write(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyMap());
        assertThat(events).isEmpty();
    }

    @Test
    void 없는_아이는_404() {
        assertThatThrownBy(() -> service.mark(99L)).isInstanceOf(ApiException.class);
    }
}
