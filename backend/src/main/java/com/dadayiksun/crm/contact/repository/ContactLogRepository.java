package com.dadayiksun.crm.contact.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** contact_log 쓰기. contact는 이 테이블에만 쓴다 (K17 ①). */
public interface ContactLogRepository {

    void insert(Row row);

    /** 예정 항목 하나의 연락 결과 한 행. 같은 저장의 행들은 같은 {@code groupId} */
    record Row(String groupId, long guardianId, long petId, long scheduleId, String method, String result,
               String talkedWith, String memo, LocalDate nextContactDate, LocalDateTime contactedAt, long createdBy) {
    }
}
