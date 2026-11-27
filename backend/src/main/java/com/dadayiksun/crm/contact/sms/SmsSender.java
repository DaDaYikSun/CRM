package com.dadayiksun.crm.contact.sms;

/**
 * 문자 보내기 (C14, NFR-EXT-001). MVP는 {@link MockSmsSender}가 실제로 보내지 않는다 (POL-CNT-005).
 * 실제 발송(v2)은 이 인터페이스의 구현만 바꾼다. 연락 저장 트랜잭션 밖(커밋 뒤)에서 부른다 (K21).
 */
public interface SmsSender {

    /** 실패하면 예외를 던진다. 부르는 쪽은 연락 결과를 되돌리지 않고 응답에 실패를 싣는다 */
    void send(String phone, String body);
}
