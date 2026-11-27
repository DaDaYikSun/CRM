package com.dadayiksun.crm.cycle.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.dadayiksun.crm.cycle.domain.VaccinePhase.BOOSTER;
import static com.dadayiksun.crm.cycle.domain.VaccinePhase.PREVENTIVE;
import static com.dadayiksun.crm.cycle.domain.VaccinePhase.PRIMARY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 접종 예정일 손계산 표 (FR-CYC-001 확인 방법, DB 설계 4-7 계산 규칙, K23 · K24).
 * 기대 날짜는 모두 달력으로 직접 센 값이다.
 */
class VaccineDueDateCalculatorTest {

    /** 광견병 (접종 규칙 표 1장 확정 값) */
    static final List<VaccineRule> RABIES = List.of(
            new VaccineRule(PRIMARY, 90, 365, 365, 1),
            new VaccineRule(BOOSTER, 365, null, 365, null));

    /** 심장사상충 예방약 (접종 규칙 표 1장 확정 값) */
    static final List<VaccineRule> HEARTWORM = List.of(
            new VaccineRule(PREVENTIVE, 56, null, 30, null));

    /** 구충제 (K23: 생후 8주까지 14일, 6개월까지 30일, 그 뒤 90일) */
    static final List<VaccineRule> DEWORMING = List.of(
            new VaccineRule(PREVENTIVE, 14, 56, 14, null),
            new VaccineRule(PREVENTIVE, 56, 182, 30, null),
            new VaccineRule(PREVENTIVE, 182, null, 90, null));

    /**
     * 종합백신 모양의 규칙 (테스트 전용 값). 실제 기초접종 간격 · 추가접종 주기는
     * 접종 규칙 표 2장 2 · 4번이 미확정이라 시드에서 정한다. 여기서는 계산 순서만 본다.
     */
    static final List<VaccineRule> DHPPL_SHAPE = List.of(
            new VaccineRule(BOOSTER, 365, null, 365, null),   // 순서를 섞어 넣어도 나이 순으로 본다
            new VaccineRule(PRIMARY, 42, 365, 21, 5));

    static final LocalDate BIRTH = LocalDate.of(2025, 1, 1);

    @Nested
    @DisplayName("첫 예정일 = 생년월일 + 첫 행 age_from_days")
    class FirstDueDate {

        @Test
        void 광견병은_생후_90일() {
            assertThat(VaccineDueDateCalculator.firstDueDate(BIRTH, RABIES))
                    .contains(LocalDate.of(2025, 4, 1));
        }

        @Test
        void 심장사상충은_생후_56일() {
            assertThat(VaccineDueDateCalculator.firstDueDate(BIRTH, HEARTWORM))
                    .contains(LocalDate.of(2025, 2, 26));
        }

        @Test
        void 구충제는_생후_14일() {
            assertThat(VaccineDueDateCalculator.firstDueDate(BIRTH, DEWORMING))
                    .contains(LocalDate.of(2025, 1, 15));
        }

        @Test
        void 규칙_순서를_섞어_넣어도_가장_이른_나이_행을_쓴다() {
            assertThat(VaccineDueDateCalculator.firstDueDate(BIRTH, DHPPL_SHAPE))
                    .contains(LocalDate.of(2025, 2, 12));
        }

        @Test
        void 기록이_없으면_다음_예정일도_첫_예정일이다() {
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, RABIES, List.of()))
                    .contains(LocalDate.of(2025, 4, 1));
        }

        @Test
        void 규칙이_없는_종류는_예정을_만들지_않는다() {
            assertThat(VaccineDueDateCalculator.firstDueDate(BIRTH, List.of())).isEmpty();
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, List.of(), List.of(BIRTH))).isEmpty();
        }
    }

    @Nested
    @DisplayName("FR-CYC-001: 어린 강아지 · 성견 · 추정 생년월일 아이")
    class ThreeDogs {

        @Test
        void 어린_강아지_광견병을_생후_90일에_맞으면_12개월_뒤_추가접종() {
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, RABIES,
                    List.of(LocalDate.of(2025, 4, 1))))
                    .contains(LocalDate.of(2026, 4, 1));
        }

        @Test
        void 성견은_추가접종을_매년_이어서_예정한다() {
            LocalDate birth = LocalDate.of(2022, 5, 10);
            List<LocalDate> doses = List.of(
                    LocalDate.of(2022, 8, 8), LocalDate.of(2023, 8, 8), LocalDate.of(2025, 6, 1));
            assertThat(VaccineDueDateCalculator.nextDueDate(birth, RABIES, doses))
                    .contains(LocalDate.of(2026, 6, 1));
        }

        @Test
        void 추정_생년월일_아이도_같은_규칙으로_계산한다() {
            // 추정 생년월일은 등록할 때 백엔드가 정한 날짜 (POL-REG-004). 계산은 같은 규칙
            LocalDate estimatedBirth = LocalDate.of(2025, 11, 26);
            assertThat(VaccineDueDateCalculator.firstDueDate(estimatedBirth, HEARTWORM))
                    .contains(LocalDate.of(2026, 1, 21));
        }
    }

    /** V4(#207)로 넣은 확정 값: 종합백신 2행 · 광견병 첫 예정 112일 (접종 규칙 표 3장 · 1-1 6차 접종표) */
    @Nested
    @DisplayName("확정 규칙 (V4): 국내 6차 접종표")
    class ConfirmedRules {

        final List<VaccineRule> dhppl = List.of(
                new VaccineRule(PRIMARY, 42, 365, 14, 5),
                new VaccineRule(BOOSTER, 365, null, 365, null));
        final List<VaccineRule> rabies = List.of(
                new VaccineRule(PRIMARY, 112, 365, 365, 1),
                new VaccineRule(BOOSTER, 365, null, 365, null));

        @Test
        void 종합백신_첫_예정은_생후_6주() {
            assertThat(VaccineDueDateCalculator.firstDueDate(BIRTH, dhppl)).contains(LocalDate.of(2025, 2, 12));
        }

        @Test
        void 종합백신_4차까지는_2주_뒤_5차를_채우면_1년_뒤() {
            // 6차 접종표 1~5차: 생후 42 · 56 · 70 · 84 · 98일
            List<LocalDate> four = List.of(LocalDate.of(2025, 2, 12), LocalDate.of(2025, 2, 26),
                    LocalDate.of(2025, 3, 12), LocalDate.of(2025, 3, 26));
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, dhppl, four)).contains(LocalDate.of(2025, 4, 9));

            List<LocalDate> five = new java.util.ArrayList<>(four);
            five.add(LocalDate.of(2025, 4, 9));
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, dhppl, five)).contains(LocalDate.of(2026, 4, 9));
        }

        @Test
        void 광견병_첫_예정은_생후_16주이고_6차_방문은_누락_기준_안() {
            LocalDate due = VaccineDueDateCalculator.firstDueDate(BIRTH, rabies).orElseThrow();
            assertThat(due).isEqualTo(LocalDate.of(2025, 4, 23));
            // 6차 방문(생후 112일)은 예정일 당일이라 누락 기준(예정일 + 14일) 안
            assertThat(MissJudgement.isWithinThreshold(LocalDate.of(2025, 4, 23), due, 14)).isTrue();
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, rabies, List.of(LocalDate.of(2025, 4, 23))))
                    .contains(LocalDate.of(2026, 4, 23));
        }
    }

    @Nested
    @DisplayName("K24 횟수 우선 · 광견병 예외")
    class DoseCountFirst {

        @Test
        void 기초접종_중에는_기초접종_간격() {
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, DHPPL_SHAPE,
                    List.of(LocalDate.of(2025, 2, 12))))
                    .contains(LocalDate.of(2025, 3, 5));
        }

        @Test
        void 기초접종을_1회만_맞고_400일째_접종하면_다음도_기초접종_간격() {
            List<LocalDate> doses = List.of(LocalDate.of(2025, 2, 12), LocalDate.of(2026, 2, 5)); // 400일째
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, DHPPL_SHAPE, doses))
                    .contains(LocalDate.of(2026, 2, 26));
        }

        @Test
        void 기초접종_5회를_채우면_추가접종_간격() {
            List<LocalDate> doses = List.of(
                    LocalDate.of(2025, 2, 12), LocalDate.of(2025, 3, 5), LocalDate.of(2025, 3, 26),
                    LocalDate.of(2025, 4, 16), LocalDate.of(2025, 4, 30));
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, DHPPL_SHAPE, doses))
                    .contains(LocalDate.of(2026, 4, 30));
        }

        @Test
        void 기초접종을_채운_뒤의_추가접종은_나이로_행을_찾는다() {
            List<LocalDate> doses = List.of(
                    LocalDate.of(2025, 2, 12), LocalDate.of(2025, 3, 5), LocalDate.of(2025, 3, 26),
                    LocalDate.of(2025, 4, 16), LocalDate.of(2025, 4, 30), LocalDate.of(2026, 4, 30));
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, DHPPL_SHAPE, doses))
                    .contains(LocalDate.of(2027, 4, 30));
        }

        @Test
        void 광견병을_생후_60일에_맞으면_다음은_12개월_뒤() {
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, RABIES,
                    List.of(LocalDate.of(2025, 3, 2))))
                    .contains(LocalDate.of(2026, 3, 2));
        }

        @Test
        void 접종일_순서가_섞여_있어도_마지막_접종일_기준() {
            List<LocalDate> doses = List.of(LocalDate.of(2025, 3, 5), LocalDate.of(2025, 2, 12));
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, DHPPL_SHAPE, doses))
                    .contains(LocalDate.of(2025, 3, 26));
        }

        @Test
        void 횟수_행을_채우면_사이의_반복_행보다_남은_횟수_행이_먼저() {
            // PR #190 CodeRabbit 예시: 횟수 행 · 반복 행 · 횟수 행 순서. 첫 행을 채우면 반복 행(30일)이 아니라
            // 아직 채우지 않은 횟수 행(365일)의 간격을 쓴다 (DB 설계 4-7 규칙 2, K24)
            List<VaccineRule> rules = List.of(
                    new VaccineRule(PRIMARY, 30, null, 30, 1),
                    new VaccineRule(BOOSTER, 60, 365, 30, null),
                    new VaccineRule(BOOSTER, 365, null, 365, 1));
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, rules,
                    List.of(LocalDate.of(2025, 1, 31))))             // 생후 30일
                    .contains(LocalDate.of(2026, 1, 31));             // + 365일
        }

        @Test
        void 횟수_행을_다_채우고_다음_행이_없으면_더_예정하지_않는다() {
            List<VaccineRule> once = List.of(new VaccineRule(PRIMARY, 30, null, 30, 1));
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, once,
                    List.of(LocalDate.of(2025, 2, 1)))).isEmpty();
        }
    }

    @Nested
    @DisplayName("K23 구충제: 생후 8주까지 14일, 6개월까지 30일, 그 뒤 90일")
    class Deworming {

        @Test
        void 생후_14일에_맞으면_14일_뒤() {
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, DEWORMING,
                    List.of(LocalDate.of(2025, 1, 15))))
                    .contains(LocalDate.of(2025, 1, 29));
        }

        @Test
        void 생후_60일에_맞으면_30일_뒤() {
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, DEWORMING,
                    List.of(LocalDate.of(2025, 3, 2))))
                    .contains(LocalDate.of(2025, 4, 1));
        }

        @Test
        void 생후_200일에_맞으면_90일_뒤() {
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, DEWORMING,
                    List.of(LocalDate.of(2025, 7, 20))))
                    .contains(LocalDate.of(2025, 10, 18));
        }

        @Test
        void 첫_행보다_어릴_때_맞으면_첫_행_간격() {
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, DEWORMING,
                    List.of(LocalDate.of(2025, 1, 8))))   // 생후 7일
                    .contains(LocalDate.of(2025, 1, 22));
        }

        @Test
        void 생후_56일_경계는_다음_구간() {
            assertThat(VaccineDueDateCalculator.nextDueDate(BIRTH, DEWORMING,
                    List.of(LocalDate.of(2025, 2, 26))))  // 생후 56일
                    .contains(LocalDate.of(2025, 3, 28));
        }
    }

    @Nested
    @DisplayName("예외")
    class Exceptions {

        @Test
        void 생년월일보다_앞선_접종_기록은_받지_않는다() {
            assertThatThrownBy(() -> VaccineDueDateCalculator.nextDueDate(BIRTH, RABIES,
                    List.of(LocalDate.of(2024, 12, 31))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 생년월일이_비면_계산하지_않는다() {
            assertThatThrownBy(() -> VaccineDueDateCalculator.firstDueDate(null, RABIES))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 잘못된_규칙_행은_만들_수_없다() {
            assertThatThrownBy(() -> new VaccineRule(PRIMARY, 42, 42, 21, 5))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new VaccineRule(PRIMARY, 42, 365, 0, 5))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new VaccineRule(PRIMARY, 42, 365, 21, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
