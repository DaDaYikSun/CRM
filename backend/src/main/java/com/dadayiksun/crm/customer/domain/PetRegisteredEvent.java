package com.dadayiksun.crm.customer.domain;

/**
 * '아이 등록됨' 이벤트 (API 설계 4-1, 팀 결정 K13).
 * customer는 아이를 저장한 트랜잭션 안에서 이 이벤트를 내기만 한다. cycle(#144)이 같은 트랜잭션에서 듣고
 * C4 {@code createInitialSchedules(petId)}로 첫 예정 일정을 만든다. customer는 cycle을 import하거나 부르지 않는다.
 *
 * <p>듣는 쪽은 {@code @EventListener}(같은 트랜잭션, 실패하면 등록도 되돌림)로 받는다.
 */
public record PetRegisteredEvent(Long petId) {
}
