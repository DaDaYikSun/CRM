package com.dadayiksun.crm.contact.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** contact_log 쓰기. contact는 이 테이블에만 쓴다 (K17 ①). */
public interface ContactLogRepository {

    void insert(Row row);

    /** 2일차 안내 문자 모의 발송 기록 한 행 (POL-TGT-006, POL-CNT-005). 연락 결과와 같은 트랜잭션 (K21) */
    void insertMockSms(MockSmsRow row);

    /** result는 없고 method = SMS. 보낸 문구 원문을 남긴다 (DB 설계 4-11 sms_body) */
    record MockSmsRow(String groupId, long guardianId, long petId, long scheduleId, String template, String body,
                      LocalDateTime contactedAt, long createdBy) {
    }

    /** 예정 항목 하나의 연락 결과 한 행. 같은 저장의 행들은 같은 {@code groupId} */
    record Row(String groupId, long guardianId, long petId, long scheduleId, String method, String result,
               String talkedWith, String memo, LocalDate nextContactDate, LocalDateTime contactedAt, long createdBy) {
    }
}
