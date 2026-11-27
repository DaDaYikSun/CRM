package com.dadayiksun.crm.contact.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 모의 발송 (POL-CNT-005): 실제로 보내지 않는다. 기록은 contact_log 문자 행이 남긴다. 번호 · 문구는 로그에 남기지 않는다 */
@Component
public class MockSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    @Override
    public void send(String phone, String body) {
        log.info("문자 모의 발송: {}자 (실제로 보내지 않음)", body.length());
    }
}
