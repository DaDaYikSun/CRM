package com.dadayiksun.crm.target.domain;

import static com.dadayiksun.crm.target.domain.TargetFixtures.WEEK_END;
import static com.dadayiksun.crm.target.domain.TargetFixtures.row;
import static com.dadayiksun.crm.target.domain.TargetTab.MISSED;
import static com.dadayiksun.crm.target.domain.TargetTab.TODAY;
import static com.dadayiksun.crm.target.domain.TargetTab.WEEK;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.target.domain.TargetCandidate.ContactState;

/** 정책 2-4 표 (POL-TGT-001~010)와 DB 설계 5-2를 조건마다 하나씩 (FR-TGT-003 확인 방법). */
class TargetRulesTest {

    private static boolean in(TargetCandidate c, TargetTab tab) {
        return TargetRules.belongs(c, tab, TargetFixtures.TODAY, WEEK_END);
    }

    @Nested
    @DisplayName("POL-TGT-001: 오늘 · 이번 주(월~일)")
    class Period {

        @Test
        void 예정일이_오늘이면_오늘_탭과_이번_주_탭() {
            TargetCandidate c = row(1).due(TargetFixtures.TODAY).build();
            assertThat(in(c, TODAY)).isTrue();
            assertThat(in(c, WEEK)).isTrue();
            assertThat(in(c, MISSED)).isFalse();
        }

        @Test
        void 예정일이_지났고_누락_판정_전이면_오늘_탭() {
            assertThat(in(row(1).due(TargetFixtures.TODAY.minusDays(14)).build(), TODAY)).isTrue();
        }

        @Test
        void 예정일이_이번_주_일요일이면_이번_주_탭만() {
            TargetCandidate c = row(1).due(WEEK_END).build();
            assertThat(in(c, TODAY)).isFalse();
            assertThat(in(c, WEEK)).isTrue();
        }

        @Test
        void 예정일이_다음_주_월요일이면_어느_탭에도_없다() {
            TargetCandidate c = row(1).due(WEEK_END.plusDays(1)).build();
            assertThat(in(c, TODAY)).isFalse();
            assertThat(in(c, WEEK)).isFalse();
        }
    }

    @Nested
    @DisplayName("누락 탭: 저장된 missed_at만 (T13, POL-MISS-001)")
    class Missed {

        @Test
        void 누락_판정이_저장되면_누락_탭에만_나온다() {
            TargetCandidate c = row(1).due(TargetFixtures.TODAY.minusDays(15)).missedAt(TargetFixtures.TODAY).build();
            assertThat(in(c, MISSED)).isTrue();
            assertThat(in(c, TODAY)).isFalse();
            assertThat(in(c, WEEK)).isFalse();
        }

        @Test
        void 예정일_14일_뒤_당일은_판정이_없어_누락_탭에_없다() {
            TargetCandidate c = row(1).due(TargetFixtures.TODAY.minusDays(14)).build();
            assertThat(in(c, MISSED)).isFalse();
        }

        @Test
        void POL_TGT_007_예약하고_오지_않아_누락이_저장되면_누락_탭에_다시_나온다() {
            TargetCandidate c = row(1).due(TargetFixtures.TODAY.minusDays(15)).missedAt(TargetFixtures.TODAY)
                    .state(ContactState.BOOKED).build();
            assertThat(in(c, MISSED)).isTrue();
        }

        @Test
        void 거절_다른_병원_2일차_마침은_누락_탭에도_없다() {
            for (ContactState s : new ContactState[] {ContactState.REFUSED, ContactState.OTHER_HOSPITAL, ContactState.CLOSED}) {
                TargetCandidate c = row(1).missedAt(TargetFixtures.TODAY).state(s).build();
                assertThat(in(c, MISSED)).as(s.name()).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("POL-TGT-002 ~ 004: 늘 빠짐")
    class AlwaysExcluded {

        @Test
        void POL_TGT_002_무지개다리_아이는_어느_탭에도_없다() {
            TargetCandidate c = row(1).rainbow().build();
            TargetCandidate missed = row(2).rainbow().missedAt(TargetFixtures.TODAY).build();
            assertThat(in(c, TODAY)).isFalse();
            assertThat(in(c, WEEK)).isFalse();
            assertThat(in(missed, MISSED)).isFalse();
        }

        @Test
        void POL_TGT_003_같은_집_보류는_보류_끝날까지_빠지고_다음_날_올라온다() {
            assertThat(in(row(1).holdUntil(TargetFixtures.TODAY).build(), TODAY)).isFalse();
            assertThat(in(row(1).holdUntil(TargetFixtures.TODAY.plusDays(10)).missedAt(TargetFixtures.TODAY).build(), MISSED)).isFalse();
            assertThat(in(row(1).holdUntil(TargetFixtures.TODAY.minusDays(1)).build(), TODAY)).isTrue();
        }

        @Test
        void POL_TGT_004_연락_원치_않음_보호자는_빠진다() {
            assertThat(in(row(1).noContact().build(), TODAY)).isFalse();
            assertThat(in(row(1).noContact().missedAt(TargetFixtures.TODAY).build(), MISSED)).isFalse();
        }
    }

    @Nested
    @DisplayName("POL-TGT-005 ~ 009: 연락 결과로 빠짐")
    class ByContact {

        @Test
        void POL_TGT_005_006_다음_연락_예정일_전에는_빠지고_그날_올라온다() {
            assertThat(in(row(1).state(ContactState.NO_ANSWER).nextContact(TargetFixtures.TODAY.plusDays(1)).build(), TODAY)).isFalse();
            assertThat(in(row(1).state(ContactState.NO_ANSWER).nextContact(TargetFixtures.TODAY).build(), TODAY)).isTrue();
            assertThat(in(row(1).missedAt(TargetFixtures.TODAY).nextContact(TargetFixtures.TODAY.plusDays(3)).build(), MISSED)).isFalse();
        }

        @Test
        void POL_TGT_007_008_009_예약함_거절_다른_병원_2일차_마침은_이번_주기_동안_빠진다() {
            for (ContactState s : new ContactState[] {ContactState.BOOKED, ContactState.REFUSED, ContactState.OTHER_HOSPITAL, ContactState.CLOSED}) {
                assertThat(in(row(1).state(s).build(), TODAY)).as(s.name()).isFalse();
                assertThat(in(row(1).state(s).build(), WEEK)).as(s.name()).isFalse();
            }
        }

        @Test
        void 번호_오류는_연락_결과로_빠지지_않는다() {
            assertThat(in(row(1).state(ContactState.WRONG_NUMBER).build(), TODAY)).isTrue();
        }

        @Test
        void POL_TGT_010_오늘_결과를_남긴_줄은_그날은_오늘_탭에_남는다() {
            TargetCandidate c = row(1).state(ContactState.BOOKED).processedToday().build();
            assertThat(in(c, TODAY)).isTrue();
            assertThat(in(c, WEEK)).isTrue();
        }

        @Test
        void 오늘_처리했어도_무지개다리_보류_연락_원치_않음은_빠진다() {
            assertThat(in(row(1).processedToday().noContact().build(), TODAY)).isFalse();
        }
    }
}
