package com.dadayiksun.crm.contact.service;

import org.springframework.stereotype.Service;

/**
 * C14 결과별 후속 처리 (FR-CNT-003). 연락 결과 저장과 같은 트랜잭션에서 불린다 (API 설계 3-6).
 * 규칙(주기 상태 · 다음 연락 예정일 · 2일차 마침 · 모의 발송 기록 · 전환 판정)은 #154가 채운다.
 * {@code SmsSender}는 이 안에서 부르지 않는다. 트랜잭션 밖(커밋 뒤)에서 부른다 (K21).
 */
@Service
public class ContactFollowUpService {

    /** shortcut: 지금은 아무것도 하지 않는다, #154 머지 때 규칙을 넣는다 */
    public void apply(String contactGroupId) {
    }
}
