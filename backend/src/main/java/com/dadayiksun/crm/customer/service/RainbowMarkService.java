package com.dadayiksun.crm.customer.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.setting.SettingKey;
import com.dadayiksun.crm.common.setting.SettingService;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.Pet;
import com.dadayiksun.crm.customer.domain.PetProfileChangedEvent;
import com.dadayiksun.crm.customer.dto.RainbowResponse;
import com.dadayiksun.crm.customer.repository.PetRepository;

/**
 * {@code POST /api/pets/{id}/rainbow} 무지개다리로 표시 (C21, FR-CUS-007, POL-END-001 · 003, API 설계 3-3).
 *
 * <p>관리자 · 데스크만 할 수 있다 (역할 검사는 RoleAccessRules). 같은 집 아직 무지개다리가 아닌 다른 아이에게
 * 오늘 + {@code RAINBOW_HOLD_DAYS}까지 연락 보류를 건다. 이미 더 늦은 보류가 있으면 그대로 둔다 (기준값을 줄여도
 * 저장된 보류는 줄이지 않는다, POL-END-003). 마감은 STATUS_CHANGE 이력의 {@code holdUntil}로 남겨 되돌리기가 읽는다.
 * 예정 일정 정리는 cycle이 '프로필 바뀜' 이벤트를 듣고 한다 (K13).
 *
 * <p>이미 무지개다리인 아이는 아무것도 하지 않는다 (이력 · 보류 그대로, 같은 요청 두 번). [?] 문서에 없어 임시로 정함.
 */
@Service
public class RainbowMarkService {

    private final PetRepository pets;
    private final ChangeLogWriter changeLog;
    private final SettingService settings;
    private final KstClock clock;
    private final ApplicationEventPublisher events;

    public RainbowMarkService(PetRepository pets, ChangeLogWriter changeLog, SettingService settings, KstClock clock,
            ApplicationEventPublisher events) {
        this.pets = pets;
        this.changeLog = changeLog;
        this.settings = settings;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public RainbowResponse mark(Long petId) {
        Long guardianId = pets.findGuardianId(petId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        List<Pet> household = pets.lockByGuardianId(guardianId);
        Pet pet = household.stream().filter(p -> p.getPetId().equals(petId)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (pet.isRainbow()) {
            return new RainbowResponse(changeLog.latestRainbowHoldUntil(petId).orElse(null), List.of());
        }

        LocalDate holdUntil = clock.today().plusDays(settings.getInt(SettingKey.RAINBOW_HOLD_DAYS));
        pet.markRainbow(clock.now());
        List<Long> held = new ArrayList<>();
        for (Pet other : household) {
            if (other.getPetId().equals(petId) || other.isRainbow()) {
                continue;
            }
            LocalDate current = other.getContactHoldUntil();
            // 이미 같거나 더 늦은 보류가 있으면 그 보류를 지킨다
            if (current == null || current.isBefore(holdUntil)) {
                other.holdContact(holdUntil, petId);
            }
            held.add(other.getPetId());
        }

        // 되돌리기가 JSON_EXTRACT로 읽는 모양: after_json = {status, holdUntil("YYYY-MM-DD")}
        changeLog.write("STATUS_CHANGE", "PET", petId, Map.of("status", "ACTIVE"),
                Map.of("status", "RAINBOW", "holdUntil", holdUntil.toString()));
        // cycle은 JDBC로 아이 상태를 읽는다. 저장 전이면 아직 ACTIVE로 보여 예정 일정을 정리하지 못한다 (실제 DB로 확인)
        pets.flush();
        events.publishEvent(new PetProfileChangedEvent(petId));
        return new RainbowResponse(holdUntil, held);
    }
}
