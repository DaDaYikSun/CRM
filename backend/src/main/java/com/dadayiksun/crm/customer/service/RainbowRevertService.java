package com.dadayiksun.crm.customer.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.Pet;
import com.dadayiksun.crm.customer.domain.PetProfileChangedEvent;
import com.dadayiksun.crm.customer.dto.RainbowRevertResponse;
import com.dadayiksun.crm.customer.repository.PetRepository;

/**
 * {@code POST /api/pets/{id}/rainbow-revert} 무지개다리 되돌리기 (C10, FR-CUS-008, POL-END-004, API 설계 3-3).
 *
 * <p>관리자만 할 수 있다. 역할 검사는 #143이 API 설계 2장 허용 표를 한 곳에 옮길 때 이 주소에 걸린다 (이 서비스는 역할을 모른다).
 *
 * <p>같은 집에 아직 무지개다리인 다른 아이의 저장된 보류 마감({@code holdUntil})이 오늘 이후면 보류를 풀지 않고
 * 가장 늦은 마감 · 그 아이로 바꾼다. 마감은 지금 기준값으로 다시 계산하지 않는다 (POL-END-003, API 설계 7장 17번).
 * 되돌린 아이도 그 보류 대상이다. 상태가 이미 ACTIVE면 아무것도 하지 않는다 (같은 요청 두 번 · 이력 한 번).
 */
@Service
public class RainbowRevertService {

    private final PetRepository pets;
    private final ChangeLogWriter changeLog;
    private final KstClock clock;
    private final ApplicationEventPublisher events;

    public RainbowRevertService(PetRepository pets, ChangeLogWriter changeLog, KstClock clock,
            ApplicationEventPublisher events) {
        this.pets = pets;
        this.changeLog = changeLog;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public RainbowRevertResponse revert(Long petId) {
        // 같은 집 아이를 먼저 잠근 뒤 그 상태로 판단한다 (두 관리자가 같은 집의 다른 아이를 동시에 되돌려도 어긋나지 않게)
        Long guardianId = pets.findGuardianId(petId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        List<Pet> household = pets.lockByGuardianId(guardianId);
        Pet pet = household.stream().filter(p -> p.getPetId().equals(petId)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!pet.isRainbow()) {
            return new RainbowRevertResponse(List.of());
        }
        LocalDate today = clock.today();

        Pet remaining = null;
        LocalDate remainingUntil = null;
        for (Pet other : household) {
            if (other.getPetId().equals(petId) || !other.isRainbow()) {
                continue;
            }
            Optional<LocalDate> until = changeLog.latestRainbowHoldUntil(other.getPetId());
            // 마감 당일까지 보류가 유지된다 (holdUntil >= 오늘). 같은 마감이면 번호가 앞선 아이를 남긴다 [?]
            if (until.isPresent() && !until.get().isBefore(today)
                    && (remainingUntil == null || until.get().isAfter(remainingUntil))) {
                remaining = other;
                remainingUntil = until.get();
            }
        }

        pet.revertRainbow();
        List<Long> released = new ArrayList<>();
        for (Pet other : household) {
            if (!petId.equals(other.getContactHoldByPetId())) {
                continue;
            }
            if (remaining != null && !other.equals(remaining)) {
                other.holdContact(remainingUntil, remaining.getPetId());
            } else {
                other.holdContact(null, null);
                if (remaining == null) {
                    released.add(other.getPetId());
                }
            }
        }
        // 되돌린 아이도 남은 보류의 대상이다 (POL-END-004)
        pet.holdContact(remainingUntil, remaining == null ? null : remaining.getPetId());

        changeLog.write("STATUS_CHANGE", "PET", petId, Map.of("status", "RAINBOW"), Map.of("status", "ACTIVE"));
        // cycle은 JDBC로 아이 상태를 읽는다. 저장 전이면 아직 RAINBOW로 보여 예정 일정을 다시 만들지 않고 정리해 버린다 (#171 실제 DB 확인)
        pets.flush();
        events.publishEvent(new PetProfileChangedEvent(petId));
        return new RainbowRevertResponse(released);
    }
}
