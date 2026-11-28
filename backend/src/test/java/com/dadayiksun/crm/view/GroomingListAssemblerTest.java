package com.dadayiksun.crm.view;

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

/** S-11 미용 목록 (FR-VIS-002, API 설계 3장 GET /api/grooming): 탭 기준 날짜 · 정렬 · 오늘 미용 줄 · 페이지. */
class GroomingListAssemblerTest {

    /** 2026-11-25(수) 10시 한국 시간. 이번 주 일요일 = 11-29 */
    private static final KstClock CLOCK = new KstClock(Clock.fixed(
            ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, KstClock.ZONE).toInstant(), KstClock.ZONE));

    private static GroomingRow due(long petId, String due) {
        return new GroomingRow(petId, "아이" + petId, "말티즈", "장모", LocalDate.of(2023, 5, 2), "보호자" + petId,
                "0000", List.of("SENSITIVE"), 100 + petId, LocalDate.parse(due), false, LocalDate.of(2026, 10, 17),
                34, "MEDIAN", false);
    }

    private static GroomingRow groomed(long petId, Long scheduleId, String due) {
        return new GroomingRow(petId, "아이" + petId, "말티즈", "장모", LocalDate.of(2023, 5, 2), "보호자" + petId,
                "0000", List.of(), scheduleId, due == null ? null : LocalDate.parse(due), false,
                LocalDate.of(2026, 11, 25), null, null, true);
    }

    @Test
    void 오늘_탭은_오늘까지_이번_주_탭은_일요일까지_예정일로_묻고_오늘_미용도_묻는다() {
        List<LocalDate[]> asked = new ArrayList<>();
        GroomingListAssembler a = new GroomingListAssembler((dueUntil, today) -> {
            asked.add(new LocalDate[] {dueUntil, today});
            return List.of();
        }, CLOCK);

        a.assemble(GroomingTab.TODAY, PageQuery.parse(null));
        a.assemble(GroomingTab.WEEK, PageQuery.parse(null));

        assertThat(asked.get(0)).containsExactly(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 25));
        assertThat(asked.get(1)).containsExactly(LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 25));
    }

    @Test
    void 예정일_오래된_순이고_오늘_미용한_아이는_아래에_남는다() {
        List<GroomingListAssembler.Line> lines = GroomingListAssembler.lines(List.of(
                due(1, "2026-11-25"), groomed(2, 202L, "2026-11-20"), due(3, "2026-11-10"), due(4, "2026-11-22")));

        assertThat(lines).extracting(GroomingListAssembler.Line::petId).containsExactly(3L, 4L, 1L, 2L);
        assertThat(lines.get(3).groomedToday()).isTrue();
        assertThat(lines.get(3).scheduleId()).isEqualTo(202L);
        assertThat(lines.get(0).groomInterval().days()).isEqualTo(34);
        assertThat(lines.get(0).groomInterval().basis()).isEqualTo("MEDIAN");
        assertThat(lines.get(3).groomInterval()).isNull();
    }

    @Test
    void 한_아이는_한_줄이고_오늘_미용_줄이_이긴다() {
        // 오늘 미용을 기록해 완료된 주기(오늘 미용 줄)와, 기록으로 생긴 다음 주기(이번 주 안이면 예정 줄)가 같이 온다
        List<GroomingListAssembler.Line> lines = GroomingListAssembler.lines(List.of(
                due(1, "2026-11-28"), groomed(1, 101L, "2026-11-20")));

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).groomedToday()).isTrue();
        assertThat(lines.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void 주기_없이_기록한_오늘_미용도_남고_예정일이_없으면_맨_아래() {
        List<GroomingListAssembler.Line> lines = GroomingListAssembler.lines(List.of(
                groomed(5, null, null), groomed(6, 206L, "2026-11-24"), groomed(5, 205L, "2026-11-23")));

        assertThat(lines).extracting(GroomingListAssembler.Line::petId).containsExactly(5L, 6L);
        assertThat(lines.get(0).scheduleId()).isEqualTo(205L); // 같은 아이 오늘 미용 두 번이면 주기가 있는 줄
    }

    @Test
    void 아이_20개씩_나눠_보여주고_마지막보다_큰_페이지는_빈_목록과_total() {
        List<GroomingRow> rows = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            rows.add(due(i, "2026-11-25"));
        }
        GroomingListAssembler a = new GroomingListAssembler((dueUntil, today) -> rows, CLOCK);

        PageResponse<GroomingListAssembler.Line> p1 = a.assemble(GroomingTab.TODAY, PageQuery.parse("1"));
        PageResponse<GroomingListAssembler.Line> p2 = a.assemble(GroomingTab.TODAY, PageQuery.parse("2"));
        PageResponse<GroomingListAssembler.Line> p3 = a.assemble(GroomingTab.TODAY, PageQuery.parse("3"));

        assertThat(p1.items()).hasSize(20);
        assertThat(p2.items()).hasSize(1);
        assertThat(p3.items()).isEmpty();
        assertThat(p3.total()).isEqualTo(21);
    }
}
