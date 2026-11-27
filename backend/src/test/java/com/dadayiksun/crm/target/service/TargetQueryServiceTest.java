package com.dadayiksun.crm.target.service;

import static com.dadayiksun.crm.target.domain.TargetFixtures.row;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.target.domain.TargetCandidate;
import com.dadayiksun.crm.target.domain.TargetCandidate.ContactState;
import com.dadayiksun.crm.target.domain.TargetTab;
import com.dadayiksun.crm.target.dto.TargetLineResponse;
import com.dadayiksun.crm.target.dto.TargetSummaryResponse;

/** 페이지(20개, K16 ⑥) · 요약 숫자 = 탭 아이 수(FR-DASH-001) · 한국 시간 월~일 경계(POL-TGT-001). */
class TargetQueryServiceTest {

    /** 한국 시간으로 정한 순간의 시계 */
    private static KstClock kst(int y, int m, int d, int hour) {
        return new KstClock(Clock.fixed(ZonedDateTime.of(y, m, d, hour, 0, 0, 0, KstClock.ZONE).toInstant(), KstClock.ZONE));
    }

    private static TargetQueryService service(KstClock clock, List<TargetCandidate> rows) {
        return new TargetQueryService((today, weekEnd) -> rows, clock);
    }

    @Test
    void 보호자_줄_20개씩_나눠_보여주고_마지막보다_큰_페이지는_빈_목록과_total() {
        List<TargetCandidate> rows = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            rows.add(row(i).guardian(i, "보호자" + i).pet(i, "아이" + i).due(LocalDate.of(2026, 11, 25)).build());
        }
        TargetQueryService s = service(kst(2026, 11, 25, 10), rows);

        PageResponse<TargetLineResponse> p1 = s.list(TargetTab.TODAY, PageQuery.parse("1"));
        PageResponse<TargetLineResponse> p2 = s.list(TargetTab.TODAY, PageQuery.parse("2"));
        PageResponse<TargetLineResponse> p3 = s.list(TargetTab.TODAY, PageQuery.parse("3"));

        assertThat(p1.items()).hasSize(20);
        assertThat(p2.items()).hasSize(1);
        assertThat(p3.items()).isEmpty();
        assertThat(p3.total()).isEqualTo(21);
    }

    @Test
    void 요약_숫자는_각_탭의_아이_수와_같고_처리한_수는_오늘_결과를_남긴_아이_수() {
        List<TargetCandidate> rows = List.of(
                // 같은 아이 접종 2개 → 아이 수는 1
                row(1).pet(1, "보리").guardian(1, "가").due(LocalDate.of(2026, 11, 24)).build(),
                row(2).pet(1, "보리").guardian(1, "가").vaccine("광견병", 4).due(LocalDate.of(2026, 11, 25)).build(),
                // 오늘 예약함으로 처리 → 오늘 탭에 남고 처리한 수 1
                row(3).pet(2, "콩이").guardian(2, "나").state(ContactState.BOOKED).processedToday()
                        .due(LocalDate.of(2026, 11, 25)).build(),
                // 이번 주 일요일
                row(4).pet(3, "초코").guardian(3, "다").due(LocalDate.of(2026, 11, 29)).build(),
                // 누락
                row(5).pet(4, "나나").guardian(4, "라").due(LocalDate.of(2026, 11, 1)).missedAt(LocalDate.of(2026, 11, 16)).build());
        TargetQueryService s = service(kst(2026, 11, 25, 10), rows);

        TargetSummaryResponse sum = s.summary();

        assertThat(sum.today()).isEqualTo(new TargetSummaryResponse.Count(2, 1));
        assertThat(sum.week()).isEqualTo(new TargetSummaryResponse.Count(3, 1));
        assertThat(sum.missed()).isEqualTo(1);
        assertThat(petsIn(s, TargetTab.TODAY)).isEqualTo(sum.today().total());
        assertThat(petsIn(s, TargetTab.WEEK)).isEqualTo(sum.week().total());
        assertThat(petsIn(s, TargetTab.MISSED)).isEqualTo(sum.missed());
    }

    @Test
    void 이번_주는_한국_시간_월요일부터_일요일까지() {
        TargetCandidate nextMonday = row(1).due(LocalDate.of(2026, 11, 30)).build();
        // 일요일 23시(한국 시간)에는 다음 날(월요일) 예정이 이번 주 탭에 없다
        assertThat(service(kst(2026, 11, 29, 23), List.of(nextMonday)).summary().week().total()).isZero();
        // 월요일 0시(한국 시간, UTC로는 일요일 15시)에는 오늘 탭에 있다
        assertThat(service(kst(2026, 11, 30, 0), List.of(nextMonday)).summary().today().total()).isEqualTo(1);
    }

    private static long petsIn(TargetQueryService s, TargetTab tab) {
        return s.list(tab, PageQuery.parse("1")).items().stream()
                .flatMap(line -> line.pets().stream()).map(TargetLineResponse.Pet::petId).distinct().count();
    }
}
