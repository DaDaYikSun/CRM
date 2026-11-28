package com.dadayiksun.crm.view;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.customer.dto.PetDetail;

/**
 * 미용 예정 목록 조립 (S-11, FR-VIS-002, API 설계 3장 {@code GET /api/grooming} [제안]).
 * <ul>
 *   <li>오늘 탭: 미용 예정일이 오늘 이전 · 당일, 이번 주 탭: 이번 주 일요일까지 (한국 시간, 연락 대상 목록과 같은 기준 POL-TGT-001)</li>
 *   <li>예정일 오래된 순. 오늘 미용을 기록한 아이는 두 탭 모두 목록 아래에 {@code groomedToday = true}로 남는다</li>
 *   <li>한 아이는 한 줄: 오늘 미용을 기록했으면 그 줄만 (기록 뒤 생긴 다음 주기 줄은 뺀다)</li>
 *   <li>누락 판정된 미용 주기도 넣고 {@code missed}로 알린다 (API 설계 7장 6번 [제안], 화면 '누락' 배지)</li>
 * </ul>
 * 한 페이지 20개, 마지막보다 큰 페이지는 빈 목록과 total (K16 ⑥).
 */
@Component
public class GroomingListAssembler {

    private static final Comparator<GroomingRow> ORDER = Comparator
            .comparing(GroomingRow::groomedToday)
            .thenComparing(GroomingRow::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingLong(GroomingRow::petId);

    private final GroomingListQuery query;
    private final KstClock clock;

    public GroomingListAssembler(GroomingListQuery query, KstClock clock) {
        this.query = query;
        this.clock = clock;
    }

    public PageResponse<Line> assemble(GroomingTab tab, PageQuery page) {
        LocalDate today = clock.today();
        // 이번 주 일요일도 같은 today에서 구한다: 시계를 두 번 읽으면 일요일 자정에 다음 주로 넘어갈 수 있다 (PR #237 리뷰)
        LocalDate dueUntil = tab == GroomingTab.TODAY ? today : today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<Line> lines = lines(query.find(dueUntil, today));
        long total = lines.size();
        List<Line> items = page.isBeyond(total)
                ? List.of()
                : lines.subList((int) page.offset(), (int) Math.min(total, page.offset() + page.limit()));
        return PageResponse.of(items, page, total);
    }

    static List<Line> lines(List<GroomingRow> rows) {
        Map<Long, GroomingRow> byPet = new LinkedHashMap<>();
        for (GroomingRow row : rows) {
            byPet.merge(row.petId(), row, GroomingListAssembler::pick);
        }
        return byPet.values().stream().sorted(ORDER).map(Line::from).toList();
    }

    /** 같은 아이 두 줄: 오늘 미용 줄이 이긴다. 오늘 미용이 두 번이면 주기가 있는 줄 */
    private static GroomingRow pick(GroomingRow a, GroomingRow b) {
        if (a.groomedToday() != b.groomedToday()) {
            return a.groomedToday() ? a : b;
        }
        return a.scheduleId() != null ? a : b;
    }

    /** API 설계 3장 모양. {@code birthDate}(나이 표시) · {@code missed}('누락' 배지)는 화면 설계 SCR-S11-02에 맞춰 더했다. */
    public record Line(long petId, String petName, String breedName, String coatTypeName, LocalDate birthDate,
            String guardianName, String phoneLast4, List<String> guardianTags, Long scheduleId, LocalDate dueDate,
            boolean missed, LocalDate lastGroomDate, PetDetail.GroomInterval groomInterval, boolean groomedToday) {

        static Line from(GroomingRow r) {
            PetDetail.GroomInterval interval = r.groomIntervalDays() == null ? null
                    : new PetDetail.GroomInterval(r.groomIntervalDays(), r.groomIntervalBasis());
            return new Line(r.petId(), r.petName(), r.breedName(), r.coatTypeName(), r.birthDate(), r.guardianName(),
                    r.phoneLast4(), r.guardianTags(), r.scheduleId(), r.dueDate(), r.missed(), r.lastGroomDate(),
                    interval, r.groomedToday());
        }
    }
}
