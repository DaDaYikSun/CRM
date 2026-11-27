package com.dadayiksun.crm.view;

import java.time.LocalDate;
import java.util.List;

/**
 * 미용 목록(S-11)의 조회 전용 쿼리 (API 설계 4-1 R9: 여러 주인의 표를 읽기만 하는 화면 조회). 쓰지 않는다.
 * 날짜는 부르는 쪽이 한국 시간으로 정해 넘긴다 (DB의 CURDATE()를 쓰지 않음, DB 설계 1장).
 */
public interface GroomingListQuery {

    /**
     * 무지개다리가 아닌 아이의
     * <ul>
     *   <li>예정일이 {@code dueUntil} 이전 · 당일인 진행 중(OPEN) 미용 주기 (누락 판정된 주기도 포함, API 설계 7장 6번 [제안])</li>
     *   <li>방문일이 {@code today}인 미용 방문 ({@code groomedToday = true})</li>
     * </ul>
     * 한 아이가 두 쪽에 다 나올 수 있다. 줄 고르기와 정렬은 {@link GroomingListAssembler}가 한다.
     */
    List<GroomingRow> find(LocalDate dueUntil, LocalDate today);
}
