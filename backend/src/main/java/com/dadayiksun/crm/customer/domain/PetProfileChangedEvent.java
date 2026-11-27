package com.dadayiksun.crm.customer.domain;

/**
 * '프로필 바뀜' 이벤트 (API 설계 4-1, 팀 결정 K13). 생년월일 · 품종 · 털 유형이 바뀌었거나 무지개다리를 되돌렸을 때,
 * customer는 저장한 트랜잭션 안에서 이 이벤트만 낸다. cycle(#144)이 같은 트랜잭션에서 듣고 C4 {@code recalculatePet(petId)}로
 * 아직 오지 않은 예정일을 다시 계산한다 (POL-REG-009, POL-CYC-005, POL-END-004). customer는 cycle을 부르지 않는다.
 */
public record PetProfileChangedEvent(Long petId) {
}
