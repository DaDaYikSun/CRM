package com.dadayiksun.crm.cycle.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 미용 주기 손계산 표 (FR-CYC-002 확인 방법, POL-CYC-002 · 003 · 004).
 * 털 유형 기본 간격: 단모 10주(70일), 중모 7주(49일), 장모 · 곱슬 5주(35일). 기대 날짜는 달력으로 직접 센 값이다.
 */
class GroomIntervalCalculatorTest {

    static final int SHORT = 70;
    static final int MEDIUM = 49;
    static final int LONG = 35;

    private static LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    @Nested
    @DisplayName("FR-CYC-002: 미용 기록 0회 · 1회 · 2회 · 5회")
    class VisitCounts {

        @Test
        void 기록_0회는_털_유형_기본_간격() {
            assertThat(GroomIntervalCalculator.calculate(SHORT, List.of()))
                    .isEqualTo(new GroomInterval(70, GroomIntervalBasis.DEFAULT));
        }

        @Test
        void 기록_1회는_기본_간격으로_다음_예정일() {
            GroomInterval interval = GroomIntervalCalculator.calculate(MEDIUM, List.of(d(2025, 3, 1)));
            assertThat(interval).isEqualTo(new GroomInterval(49, GroomIntervalBasis.DEFAULT));
            assertThat(GroomIntervalCalculator.nextDueDate(d(2025, 3, 1), interval)).isEqualTo(d(2025, 4, 19));
        }

        @Test
        void 기록_2회는_그_간격이_주기() {
            GroomInterval interval = GroomIntervalCalculator.calculate(LONG,
                    List.of(d(2025, 3, 1), d(2025, 4, 5)));
            assertThat(interval).isEqualTo(new GroomInterval(35, GroomIntervalBasis.MEDIAN));
            assertThat(GroomIntervalCalculator.nextDueDate(d(2025, 4, 5), interval)).isEqualTo(d(2025, 5, 10));
        }

        @Test
        void 기록_5회는_간격_4개의_중앙값() {
            // 간격 35 · 35 · 42 · 28 → 정렬 28 · 35 · 35 · 42 → 중앙값 35
            List<LocalDate> visits = List.of(
                    d(2025, 1, 1), d(2025, 2, 5), d(2025, 3, 12), d(2025, 4, 23), d(2025, 5, 21));
            GroomInterval interval = GroomIntervalCalculator.calculate(LONG, visits);
            assertThat(interval).isEqualTo(new GroomInterval(35, GroomIntervalBasis.MEDIAN));
            assertThat(GroomIntervalCalculator.nextDueDate(d(2025, 5, 21), interval)).isEqualTo(d(2025, 6, 25));
        }

        @Test
        void 기록이_6회_이상이면_최근_간격_4개만_쓴다() {
            // 간격 60 · 30 · 30 · 40 · 40. 최근 4개(30 · 30 · 40 · 40)의 중앙값 35. 60까지 넣으면 40이 된다
            List<LocalDate> visits = List.of(
                    d(2024, 11, 1), d(2024, 12, 31), d(2025, 1, 30), d(2025, 3, 1), d(2025, 4, 10), d(2025, 5, 20));
            GroomInterval interval = GroomIntervalCalculator.calculate(LONG, visits);
            assertThat(interval).isEqualTo(new GroomInterval(35, GroomIntervalBasis.MEDIAN));
            assertThat(GroomIntervalCalculator.nextDueDate(d(2025, 5, 20), interval)).isEqualTo(d(2025, 6, 24));
        }
    }

    @Nested
    @DisplayName("POL-CYC-003: 기본 간격 2배가 넘는 간격은 뺀다")
    class Outliers {

        @Test
        void 두_배를_넘는_간격은_계산에서_빠진다() {
            // 단모 기본 70일, 2배 140일. 간격 70 · 173 → 173을 빼고 70
            List<LocalDate> visits = List.of(d(2025, 1, 1), d(2025, 3, 12), d(2025, 9, 1));
            GroomInterval interval = GroomIntervalCalculator.calculate(SHORT, visits);
            assertThat(interval).isEqualTo(new GroomInterval(70, GroomIntervalBasis.MEDIAN));
            assertThat(GroomIntervalCalculator.nextDueDate(d(2025, 9, 1), interval)).isEqualTo(d(2025, 11, 10));
        }

        @Test
        void 정확히_두_배인_간격은_넣는다() {
            // 장모 기본 35일, 간격 70일 (= 2배)
            assertThat(GroomIntervalCalculator.calculate(LONG, List.of(d(2025, 1, 1), d(2025, 3, 12))))
                    .isEqualTo(new GroomInterval(70, GroomIntervalBasis.MEDIAN));
        }

        @Test
        void 간격이_모두_두_배를_넘으면_기본_간격() {
            GroomInterval interval = GroomIntervalCalculator.calculate(LONG, List.of(d(2025, 1, 1), d(2025, 6, 1)));
            assertThat(interval).isEqualTo(new GroomInterval(35, GroomIntervalBasis.DEFAULT));
            assertThat(GroomIntervalCalculator.nextDueDate(d(2025, 6, 1), interval)).isEqualTo(d(2025, 7, 6));
        }
    }

    @Nested
    @DisplayName("중앙값 · 입력 정리")
    class Median {

        @Test
        void 간격이_짝수개면_가운데_두_값의_평균을_반올림() {
            // 간격 35 · 36 → 35.5 → 36
            assertThat(GroomIntervalCalculator.calculate(LONG,
                    List.of(d(2025, 1, 1), d(2025, 2, 5), d(2025, 3, 13))))
                    .isEqualTo(new GroomInterval(36, GroomIntervalBasis.MEDIAN));
        }

        @Test
        void 같은_날_기록은_한_번으로_본다() {
            assertThat(GroomIntervalCalculator.calculate(MEDIUM, List.of(d(2025, 3, 1), d(2025, 3, 1))))
                    .isEqualTo(new GroomInterval(49, GroomIntervalBasis.DEFAULT));
        }

        @Test
        void 방문일_순서가_섞여도_같다() {
            assertThat(GroomIntervalCalculator.calculate(LONG, List.of(d(2025, 4, 5), d(2025, 3, 1))))
                    .isEqualTo(new GroomInterval(35, GroomIntervalBasis.MEDIAN));
        }

        @Test
        void 기본_간격이_0_이하면_계산하지_않는다() {
            assertThatThrownBy(() -> GroomIntervalCalculator.calculate(0, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
