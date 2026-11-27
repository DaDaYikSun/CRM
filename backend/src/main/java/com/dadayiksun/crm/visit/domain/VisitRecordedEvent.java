package com.dadayiksun.crm.visit.domain;

/**
 * '방문 기록됨' 이벤트 (K13, API 설계 4-1). 방문 저장과 같은 트랜잭션에서 낸다.
 * cycle(주기 닫기 · 다음 주기, C4)과 contact(전환 판정, C14 #154)가 듣는다. visit은 듣는 쪽을 모른다.
 * 방문 값은 듣는 쪽이 visitId로 읽는다.
 */
public record VisitRecordedEvent(long visitId) {
}
