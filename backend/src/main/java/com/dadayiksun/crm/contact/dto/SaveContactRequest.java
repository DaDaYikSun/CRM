package com.dadayiksun.crm.contact.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code POST /api/contacts} 입력 (C13, API 설계 3-6). 검사는 {@code ContactSaveService}가 칸마다 한다.
 *
 * @param nextContactDate 비었고 결과가 '전화 안 받음'이면 서버가 내일로 채운다 (POL-TGT-006)
 * @param applyToHousehold 같은 집 일괄 적용 (초안 29). 이 이슈는 받기만 하고 쓰지 않는다
 */
public record SaveContactRequest(Long guardianId, String method, String talkedWith, String memo,
                                 LocalDate nextContactDate, List<Item> items, Boolean applyToHousehold) {

    /** 예정 항목(아이 × 서비스)마다 결과 하나 (POL-CNT-002) */
    public record Item(Long scheduleId, String result) {
    }
}
