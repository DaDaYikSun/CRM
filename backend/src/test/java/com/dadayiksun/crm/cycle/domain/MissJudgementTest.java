package com.dadayiksun.crm.cycle.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 누락 판정 손계산 표 (FR-CYC-004 확인 방법, POL-MISS-001 · 002, T13 · T16 · K20).
 * 예정일 2025-03-01, 기준 14일 → 제때 마지막 날 03-15, 원래 판정일 03-16.
 */
class MissJudgementTest {

    static final LocalDate DUE = LocalDate.of(2025, 3, 1);
    static final int THRESHOLD = 14;
    static final LocalDate JUDGE_DATE = LocalDate.of(2025, 3, 16);

    private static LocalDate mar(int day) {
        return LocalDate.of(2025, 3, day);
    }

    @Nested
    @DisplayName("판정 작업: 예정일 + 14일 당일은 누락이 아니고 다음 날 누락")
    class DailyJudgement {

        @Test
        void 예정일_14일_뒤_당일은_누락이_아니다() {
            assertThat(MissJudgement.judge(DUE, THRESHOLD, mar(15))).isEmpty();
        }

        @Test
        void 그_다음_날_누락이고_원래_판정일을_저장한다() {
            assertThat(MissJudgement.judge(DUE, THRESHOLD, mar(16))).contains(JUDGE_DATE);
        }

        @Test
        void 판정_작업을_건너뛰고_늦게_돌려도_같은_판정일() {
            assertThat(MissJudgement.judge(DUE, THRESHOLD, mar(20))).contains(JUDGE_DATE);
        }

        @Test
        void 주기마다_복사한_기준을_쓴다() {
            // 이 주기를 만들 때 기준이 21일이었다면 03-22까지 제때, 03-23이 판정일
            assertThat(MissJudgement.judge(DUE, 21, mar(22))).isEmpty();
            assertThat(MissJudgement.judge(DUE, 21, mar(23))).contains(mar(23));
        }
    }

    @Nested
    @DisplayName("방문 완료 때 missed_at: 판정 전/뒤 × 기준 안/밖 (K20, T16)")
    class Completion {

        @Test
        void 판정_전_기준_안이면_빈_채로_완료() {
            assertThat(MissJudgement.missedAtOnCompletion(DUE, THRESHOLD, mar(10), null)).isEmpty();
        }

        @Test
        void 판정_전_기준_밖이면_원래_판정일로_채운다_완료한_날이_아니다() {
            assertThat(MissJudgement.missedAtOnCompletion(DUE, THRESHOLD, mar(20), null)).contains(JUDGE_DATE);
        }

        @Test
        void 판정_뒤에_늦게_입력한_기준_안_방문은_판정을_지운다() {
            assertThat(MissJudgement.missedAtOnCompletion(DUE, THRESHOLD, mar(14), JUDGE_DATE)).isEmpty();
        }

        @Test
        void 판정_뒤에_온_기준_밖_방문은_판정을_남긴다() {
            assertThat(MissJudgement.missedAtOnCompletion(DUE, THRESHOLD, mar(25), JUDGE_DATE)).contains(JUDGE_DATE);
        }

        @Test
        void 기준_마지막_날_방문은_기준_안() {
            assertThat(MissJudgement.isWithinThreshold(mar(15), DUE, THRESHOLD)).isTrue();
            assertThat(MissJudgement.missedAtOnCompletion(DUE, THRESHOLD, mar(15), JUDGE_DATE)).isEmpty();
        }

        @Test
        void 예정일보다_먼저_온_방문도_기준_안() {
            assertThat(MissJudgement.isWithinThreshold(LocalDate.of(2025, 2, 25), DUE, THRESHOLD)).isTrue();
        }
    }

    @Nested
    @DisplayName("gap_days = DATEDIFF(done_date, due_date)")
    class GapDays {

        @Test
        void 늦게_오면_양수() {
            assertThat(MissJudgement.gapDays(DUE, mar(10))).isEqualTo(9);
        }

        @Test
        void 일찍_오면_음수() {
            assertThat(MissJudgement.gapDays(DUE, LocalDate.of(2025, 2, 25))).isEqualTo(-4);
        }

        @Test
        void 달을_넘겨도_날짜_차이() {
            // 2025-02-28 → 2025-03-01 은 1일 (DATE끼리 빼면 숫자 뺄셈이 되는 문제와 다름, #81)
            assertThat(MissJudgement.gapDays(LocalDate.of(2025, 2, 28), DUE)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("예외")
    class Exceptions {

        @Test
        void 누락_기준은_1_이상() {
            assertThatThrownBy(() -> MissJudgement.judge(DUE, 0, mar(20)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 오늘_날짜가_비면_판정하지_않는다() {
            assertThatThrownBy(() -> MissJudgement.judge(DUE, THRESHOLD, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
