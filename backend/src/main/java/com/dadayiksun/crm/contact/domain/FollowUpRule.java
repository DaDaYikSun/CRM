package com.dadayiksun.crm.contact.domain;

import java.time.LocalDate;

/**
 * 연락 결과별 후속 처리 규칙 (FR-CNT-003, POL-TGT-005~009, DB 설계 4-10 {@code contact_state}). 순수 함수.
 *
 * <ul>
 *   <li>전화 안 받음: 하루에 한 번만 센다 ('다음 날, 2일차까지만 매일', POL-TGT-006). 다음 연락 예정일 전에 다시 안 받으면
 *       횟수는 그대로 두고 예정일만 새로 잡는다 (같은 날 오전 · 오후 통화 등, PR #225 리뷰). 2일차에도 안 받으면 이번 주기 연락 끝(CLOSED)</li>
 *   <li>이미 끝난(CLOSED) 주기에 다시 '전화 안 받음'을 남겨도 다시 끝내지 않는다 (2일차 문자를 두 번 보내지 않게)</li>
 *   <li>예약함 · 거절 · 다른 병원 다님 · 번호 오류: 그 상태로 두고 연속 횟수는 0. 목록에서 빼는 판단은 #152가 상태로 한다</li>
 * </ul>
 */
public final class FollowUpRule {

    /** '전화 안 받음' 다음 연락 기본 간격 (POL-TGT-006, 정책 6장 고정값 1일). 프론트 contactRules와 같은 값 */
    public static final int NO_ANSWER_RETRY_DAYS = 1;
    /** 이 날째 안 받으면 이번 주기 연락을 끝낸다 = 2일차 (POL-TGT-006, 정책 6장 고정값 2일차) */
    public static final int NO_ANSWER_MAX_DAYS = 2;

    private FollowUpRule() {
    }

    /** 저장 전 그 주기의 연락 상태 (schedule) */
    public record Prev(String contactState, int noAnswerCount, LocalDate nextContactDate) {
    }

    /** 결과를 남긴 뒤 그 주기의 연락 상태. {@code closed}면 이번 저장으로 주기 연락을 끝냄 (2일차, 문자 대상) */
    public record ScheduleContact(String contactState, int noAnswerCount, LocalDate nextContactDate, boolean closed) {
    }

    /**
     * @param rowNextContactDate 이번에 남긴 다음 연락 예정일 ('전화 안 받음'이면 저장 때 채워져 있다)
     * @param contactDate        연락한 날 (한국 시간)
     */
    public static ScheduleContact after(ContactResult result, Prev prev, LocalDate rowNextContactDate,
                                        LocalDate contactDate) {
        if (result != ContactResult.NO_ANSWER) {
            return new ScheduleContact(result.name(), 0, null, false);
        }
        if ("CLOSED".equals(prev.contactState())) {
            return new ScheduleContact("CLOSED", prev.noAnswerCount(), null, false);
        }
        boolean beforeRetryDay = "NO_ANSWER".equals(prev.contactState()) && prev.nextContactDate() != null
                && contactDate.isBefore(prev.nextContactDate());
        if (beforeRetryDay) {
            return new ScheduleContact("NO_ANSWER", prev.noAnswerCount(), rowNextContactDate, false);
        }
        int count = prev.noAnswerCount() + 1;
        if (count >= NO_ANSWER_MAX_DAYS) {
            return new ScheduleContact("CLOSED", count, null, true);
        }
        return new ScheduleContact("NO_ANSWER", count, rowNextContactDate, false);
    }
}
