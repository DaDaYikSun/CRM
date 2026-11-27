package com.dadayiksun.crm.contact.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.setting.SettingService;
import com.dadayiksun.crm.contact.repository.ContactLogRepository;
import com.dadayiksun.crm.contact.repository.ContactQuery;

/**
 * 안내 문자 모의 발송 기록 (POL-CNT-005, DB 설계 4-11). 2일차 문자(#154)와 [문자 보내기](#174)가 같이 쓴다.
 * 보호자에게 한 통, 기록 한 행. 접종 항목이 있으면 예정일이 가장 이른 접종으로, 없으면 미용으로 문구를 채운다 (T4).
 * 예정일이 같은 접종이 여럿이면 주요 접종(종합백신 등)을 먼저 고른다 ('심장사상충 예방약 접종 예정일' 같은 문구를 피함, #174 결정).
 * 부르는 쪽 트랜잭션 안에서 기록만 남기고, {@code SmsSender}는 커밋 뒤 {@link ContactSmsDispatcher}가 부른다 (K21).
 */
@Component
public class MockSmsWriter {

    private final ContactQuery query;
    private final ContactLogRepository logs;
    private final SettingService settings;

    public MockSmsWriter(ContactQuery query, ContactLogRepository logs, SettingService settings) {
        this.query = query;
        this.logs = logs;
        this.settings = settings;
    }

    /** 커밋 뒤에 보낼 문자 (모의 발송 기록은 이미 남김). template = VACCINE · GROOMING */
    public record PendingSms(String phone, String body, String template) {
    }

    /** @param items 한 보호자의 예정 항목들 (비어 있지 않음) */
    @Transactional(propagation = Propagation.MANDATORY)
    public PendingSms write(String groupId, List<ContactQuery.SmsTarget> items, LocalDateTime at, long staffId) {
        ContactQuery.SmsTarget target = items.stream()
                .min(Comparator.comparing((ContactQuery.SmsTarget i) -> !"VACCINE".equals(i.serviceType()))
                        .thenComparing(ContactQuery.SmsTarget::dueDate)
                        .thenComparing(i -> !i.majorVaccine()))
                .orElseThrow();
        String template = target.serviceType();
        // shortcut: 문구 칸 채우기는 여기서 바로 한다. 문구 렌더러 C18(#180)이 생기면 그쪽으로 바꾼다
        String body = query.messageTemplate(template)
                .replace("{병원 이름}", settings.getString("HOSPITAL_NAME"))
                .replace("{병원 전화번호}", settings.getString("HOSPITAL_PHONE"))
                .replace("{아이 이름}", target.petName())
                .replace("{접종 종류}", target.vaccineTypeName() == null ? "" : target.vaccineTypeName())
                .replace("{예정일}", target.dueDate().toString());
        logs.insertMockSms(new ContactLogRepository.MockSmsRow(groupId, target.guardianId(), target.petId(),
                target.scheduleId(), template, body, at, staffId));
        return new PendingSms(target.phone(), body, template);
    }
}
