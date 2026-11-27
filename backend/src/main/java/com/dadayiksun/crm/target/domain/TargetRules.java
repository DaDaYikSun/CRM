package com.dadayiksun.crm.target.domain;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import com.dadayiksun.crm.target.domain.TargetCandidate.ContactState;

/**
 * 탭마다 목록에 넣을지 정한다. 조건은 정책 2-4(POL-TGT-001~010)와 DB 설계 5-2 그대로다. 새로 정하지 않는다.
 *
 * <ul>
 *   <li>늘 빠짐: 무지개다리(002), 같은 집 보류 중(003, {@code contact_hold_until}이 오늘 이후 · 당일), '연락 원치 않음'(004)</li>
 *   <li>연락 결과로 빠짐: 다음 연락 예정일 전(005 · 006), 예약함(007) · 거절(008) · 다른 병원(009) · 2일차 마침(006 CLOSED)</li>
 *   <li>오늘 · 이번 주 탭: 누락 판정이 없는 주기만 (#152 결정: 누락된 주기는 누락 탭에만). 오늘 결과를 남긴 줄은
 *       연락 결과로 빠지더라도 그날은 남아 아래에 둔다 (010)</li>
 *   <li>누락 탭: 저장된 판정({@code missed_at})이 있는 주기만, 날짜로 다시 계산하지 않는다 (T13). 위 제외 조건을 같이 걸되
 *       예약함은 누락 판정이 저장되면 다시 나온다 (007, #152 결정)</li>
 * </ul>
 */
public final class TargetRules {

    /** 이번 주기 동안 목록에서 빠지는 연락 결과 (DB 설계 5-2) */
    private static final Set<ContactState> CLOSED_FOR_CYCLE =
            EnumSet.of(ContactState.BOOKED, ContactState.REFUSED, ContactState.OTHER_HOSPITAL, ContactState.CLOSED);

    private TargetRules() {
    }

    public static boolean belongs(TargetCandidate c, TargetTab tab, LocalDate today, LocalDate weekEnd) {
        if (excludedAlways(c, today)) {
            return false;
        }
        return switch (tab) {
            case TODAY -> !c.missed() && !c.dueDate().isAfter(today) && (c.processedToday() || !excludedByContact(c, today));
            case WEEK -> !c.missed() && !c.dueDate().isAfter(weekEnd) && (c.processedToday() || !excludedByContact(c, today));
            case MISSED -> c.missed() && !waitingNextContact(c, today)
                    && (c.contactState() == ContactState.BOOKED || !CLOSED_FOR_CYCLE.contains(c.contactState()));
        };
    }

    /** POL-TGT-002 · 003 · 004 */
    static boolean excludedAlways(TargetCandidate c, LocalDate today) {
        return !c.petActive()
                || (c.contactHoldUntil() != null && !c.contactHoldUntil().isBefore(today))
                || c.tagNoContact();
    }

    /** POL-TGT-005 ~ 009 */
    static boolean excludedByContact(TargetCandidate c, LocalDate today) {
        return waitingNextContact(c, today) || CLOSED_FOR_CYCLE.contains(c.contactState());
    }

    /** 다음 연락 예정일이 오늘 뒤면 그날까지 빠진다 (당일이면 다시 올라옴) */
    private static boolean waitingNextContact(TargetCandidate c, LocalDate today) {
        return c.nextContactDate() != null && c.nextContactDate().isAfter(today);
    }
}
