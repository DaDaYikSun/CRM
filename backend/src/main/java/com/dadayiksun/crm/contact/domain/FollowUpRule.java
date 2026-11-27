package com.dadayiksun.crm.contact.domain;

import java.time.LocalDate;

/**
 * 연락 결과별 후속 처리 규칙 (FR-CNT-003, POL-TGT-005~009, DB 설계 4-10 {@code contact_state}). 순수 함수.
 *
 * <ul>
 *   <li>전화 안 받음: 연속 횟수 +1, 다음 연락 예정일(기본 1일 뒤)까지 대상에서 뺀다. 2일차에도 안 받으면 이번 주기 연락 끝(CLOSED)</li>
 *   <li>예약함 · 거절 · 다른 병원 다님 · 번호 오류: 그 상태로 두고 연속 횟수는 0. 목록에서 빼는 판단은 #152가 상태로 한다</li>
 * </ul>
 */
public final class FollowUpRule {

    /** '전화 안 받음' 다음 연락 기본 간격 (POL-TGT-006, 정책 6장 고정값 1일). 프론트 contactRules와 같은 값 */
    public static final int NO_ANSWER_RETRY_DAYS = 1;
    /** 이 횟수째 안 받으면 이번 주기 연락을 끝낸다 = 2일차 (POL-TGT-006, 정책 6장 고정값 2일차) */
    public static final int NO_ANSWER_MAX_DAYS = 2;

    private FollowUpRule() {
    }

    /** 결과를 남긴 뒤 그 주기의 연락 상태. {@code closed}면 이번 주기 연락 끝 (2일차) */
    public record ScheduleContact(String contactState, int noAnswerCount, LocalDate nextContactDate, boolean closed) {
    }

    /**
     * @param prevNoAnswerCount 저장 전 그 주기의 연속 '전화 안 받음' 횟수
     * @param nextContactDate   그 행에 남긴 다음 연락 예정일 ('전화 안 받음'이면 저장 때 채워져 있다)
     */
    public static ScheduleContact after(ContactResult result, int prevNoAnswerCount, LocalDate nextContactDate) {
        if (result != ContactResult.NO_ANSWER) {
            return new ScheduleContact(result.name(), 0, null, false);
        }
        int count = prevNoAnswerCount + 1;
        if (count >= NO_ANSWER_MAX_DAYS) {
            return new ScheduleContact("CLOSED", count, null, true);
        }
        return new ScheduleContact("NO_ANSWER", count, nextContactDate, false);
    }
}
