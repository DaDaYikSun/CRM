package com.dadayiksun.crm.contact.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.dadayiksun.crm.contact.dto.SaveContactResponse;
import com.dadayiksun.crm.contact.sms.SmsSender;

/**
 * 연락 저장이 커밋된 뒤 2일차 안내 문자를 보내고 응답을 만든다 (K21, API 설계 3-6).
 * 트랜잭션이 없는 빈이라 {@link ContactSaveService#save}가 돌아온 때는 이미 커밋됐다.
 * {@link SmsSender}가 실패해도 연락 결과는 그대로 두고 5xx가 아니라 성공 응답의 {@code mockSms.sent = false}로 알린다.
 * 같은 요청을 다시 보내면 IdempotencyFilter(K15)가 처음 응답을 돌려줘 여기까지 오지 않으므로 두 번 보내지 않는다.
 */
@Component
public class ContactSmsDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ContactSmsDispatcher.class);

    private final SmsSender sms;

    public ContactSmsDispatcher(SmsSender sms) {
        this.sms = sms;
    }

    public SaveContactResponse sendAfterCommit(ContactSaveService.Saved saved) {
        ContactFollowUpService.Outcome outcome = saved.followUp();
        ContactFollowUpService.PendingSms pending = outcome.pendingSms();
        SaveContactResponse.MockSms mockSms = null;
        if (pending != null) {
            boolean sent;
            try {
                sms.send(pending.phone(), pending.body());
                sent = true;
            } catch (RuntimeException e) {
                log.warn("2일차 안내 문자를 보내지 못했다 (연락 결과는 저장됨): {}", e.toString());
                sent = false;
            }
            mockSms = new SaveContactResponse.MockSms(sent, pending.template());
        }
        return new SaveContactResponse(saved.contactGroupId(),
                new SaveContactResponse.FollowUp(outcome.closedScheduleIds(), mockSms));
    }
}
