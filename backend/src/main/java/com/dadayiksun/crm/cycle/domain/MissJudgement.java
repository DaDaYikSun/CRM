package com.dadayiksun.crm.cycle.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 누락 판정 규칙 (FR-CYC-004, POL-MISS-001 · 002, 팀 결정 T13 · T16 · K20, DB 설계 4-10 · 5-1).
 *
 * <p>누락 기준(일)은 주기를 만들 때 복사해 둔 `schedule.miss_threshold_days`를 넘긴다. 설정값을 바꿔도
 * 이미 열린 주기는 자기 기준을 쓴다 (POL-MISS-001). '오늘'은 한국 시간(Asia/Seoul) 날짜를 넘긴다
 * (DB의 `CURDATE()`를 쓰지 않는다, DB 설계 1장).
 *
 * <ul>
 *   <li>기준 안 = 방문일 ≤ 예정일 + 기준 (그날까지는 제때 온 것)</li>
 *   <li>판정: 진행 중이고 판정이 없고 예정일 + 기준 &lt; 오늘이면 누락. 저장값은 원래 판정일
 *       = 예정일 + 기준 + 1 (판정 작업을 늦게 다시 돌려도 같은 값)</li>
 *   <li>방문으로 완료할 때: 기준 안이면 판정을 지운다(T16, 늦게 입력한 기준 안 방문 포함).
 *       기준 밖이면 있던 판정을 두고, 없으면 원래 판정일로 채운다(완료한 날을 넣지 않는다)</li>
 * </ul>
 */
public final class MissJudgement {

    private MissJudgement() {
    }

    /** 제때 온 것으로 보는 마지막 날 = 예정일 + 기준 */
    public static LocalDate onTimeDeadline(LocalDate dueDate, int thresholdDays) {
        requireArgs(dueDate, thresholdDays);
        return dueDate.plusDays(thresholdDays);
    }

    /** 원래 판정일 = 예정일 + 기준 + 1 (`missed_at`에 넣는 값) */
    public static LocalDate originalJudgeDate(LocalDate dueDate, int thresholdDays) {
        return onTimeDeadline(dueDate, thresholdDays).plusDays(1);
    }

    /** 방문일이 기준 안인지 (예정일보다 이른 방문도 기준 안) */
    public static boolean isWithinThreshold(LocalDate visitDate, LocalDate dueDate, int thresholdDays) {
        if (visitDate == null) {
            throw new IllegalArgumentException("방문일이 비어 있다");
        }
        return !visitDate.isAfter(onTimeDeadline(dueDate, thresholdDays));
    }

    /**
     * 매일 판정 작업이 진행 중(OPEN)이고 아직 판정이 없는 주기에 쓴다.
     * 누락이면 저장할 원래 판정일, 아니면 비어 있다.
     */
    public static Optional<LocalDate> judge(LocalDate dueDate, int thresholdDays, LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("오늘 날짜가 비어 있다");
        }
        if (onTimeDeadline(dueDate, thresholdDays).isBefore(today)) {
            return Optional.of(originalJudgeDate(dueDate, thresholdDays));
        }
        return Optional.empty();
    }

    /**
     * 방문으로 주기를 완료할 때의 `missed_at` (K20, T16).
     *
     * @param currentMissedAt 지금 저장된 판정 (없으면 null)
     * @return 완료 뒤 저장할 판정. 비어 있으면 `missed_at = NULL`
     */
    public static Optional<LocalDate> missedAtOnCompletion(LocalDate dueDate, int thresholdDays,
                                                           LocalDate visitDate, LocalDate currentMissedAt) {
        if (isWithinThreshold(visitDate, dueDate, thresholdDays)) {
            return Optional.empty();
        }
        if (currentMissedAt != null) {
            return Optional.of(currentMissedAt);
        }
        return Optional.of(originalJudgeDate(dueDate, thresholdDays));
    }

    /** 예정일 대비 실제 방문 차이(일) = DATEDIFF(done_date, due_date) (POL-MISS-002) */
    public static int gapDays(LocalDate dueDate, LocalDate doneDate) {
        if (dueDate == null || doneDate == null) {
            throw new IllegalArgumentException("예정일과 완료일이 필요하다");
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(dueDate, doneDate));
    }

    private static void requireArgs(LocalDate dueDate, int thresholdDays) {
        if (dueDate == null) {
            throw new IllegalArgumentException("예정일이 비어 있다");
        }
        if (thresholdDays < 1) {
            throw new IllegalArgumentException("누락 기준은 1 이상이다");
        }
    }
}
