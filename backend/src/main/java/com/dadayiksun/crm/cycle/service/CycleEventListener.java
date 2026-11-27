package com.dadayiksun.crm.cycle.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.dadayiksun.crm.customer.domain.PetRegisteredEvent;

/**
 * 아래 도메인이 내는 이벤트를 같은 트랜잭션에서 듣는다 (K13, API 설계 4-1). 실패하면 내는 쪽 저장도 되돌아간다.
 * '방문 기록됨'(visit, #148) · '프로필 바뀜'(customer, #149 · #151)은 그 이벤트가 생기면 여기서
 * {@link CycleService#onVisitRecorded} · {@link CycleService#recalculatePet}을 부른다.
 */
@Component
public class CycleEventListener {

    private final CycleService cycle;

    public CycleEventListener(CycleService cycle) {
        this.cycle = cycle;
    }

    @EventListener
    public void onPetRegistered(PetRegisteredEvent event) {
        cycle.createInitialSchedules(event.petId());
    }
}
