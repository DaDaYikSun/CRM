package com.dadayiksun.crm.cycle.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.dadayiksun.crm.customer.domain.PetProfileChangedEvent;
import com.dadayiksun.crm.customer.domain.PetRegisteredEvent;

/**
 * 아래 도메인이 내는 이벤트를 같은 트랜잭션에서 듣는다 (K13, API 설계 4-1). 실패하면 내는 쪽 저장도 되돌아간다.
 * '아이 등록됨'(#145) · '프로필 바뀜'(#151)을 듣는다. '방문 기록됨'(visit #148, {@code VisitRecordedEvent(visitId)})이
 * 생기면 여기서 {@link CycleService#onVisitRecorded}를 부른다.
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

    /** 생년월일 · 품종 · 털 유형이 바뀌었거나 무지개다리를 되돌렸을 때 (#151, POL-CYC-005 · POL-END-004) */
    @EventListener
    public void onPetProfileChanged(PetProfileChangedEvent event) {
        cycle.recalculatePet(event.petId());
    }
}
