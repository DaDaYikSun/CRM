package com.dadayiksun.crm.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 반려견 한 마리의 더미 기록(방문 · 연락)을 시간 순서로 늘어놓는다 (#147 2단계).
 * 적재기는 이 순서대로 방문을 넣고 cycle에 '방문 기록됨'을 알리며, 연락은 그때 열린 예정 일정에 붙인다.
 *
 * <ul>
 *   <li>등록일 전 방문은 등록할 때 같이 입력한 과거 기록으로 본다. 첫 예정 일정을 만들기 전에 넣어 첫 예정일 계산에 쓴다.</li>
 *   <li>등록일 전 연락은 붙일 예정 일정이 없어 넣지 않는다.</li>
 *   <li>같은 날은 시각 순서. 방문 시각은 입력 시각(created_at)의 시:분을 방문일에 붙여 쓴다.</li>
 * </ul>
 */
final class DummyTimeline {

    enum Kind { VISIT, CONTACT }

    /** service: VACCINE · GROOMING. row: CSV 한 줄 */
    record Event(Kind kind, String service, LocalDateTime at, Map<String, String> row) {
    }

    record Plan(List<Event> beforeRegistration, List<Event> afterRegistration, int contactsBeforeRegistration) {
    }

    private DummyTimeline() {
    }

    static Event visit(String service, Map<String, String> row) {
        LocalTime time = LocalDateTime.parse(row.get("created_at").replace(' ', 'T')).toLocalTime();
        return new Event(Kind.VISIT, service, LocalDate.parse(row.get("visit_date")).atTime(time), row);
    }

    static Event contact(Map<String, String> row) {
        return new Event(Kind.CONTACT, row.get("service_type"),
                LocalDateTime.parse(row.get("contacted_at").replace(' ', 'T')), row);
    }

    /**
     * 여러 아이의 등록 뒤 기록을 한 줄로: 시각 → 방문 먼저 → 같은 연락 묶음끼리 붙여서 → 아이 번호.
     * 같은 집 묶음 연락은 같은 시각 · 같은 contact_group_id라 이어서 나온다 (적재기가 묶음 끝에서 후속 처리를 한 번 부른다).
     */
    static List<Event> merge(List<Event> events) {
        List<Event> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(Event::at).thenComparing(Event::kind)
                .thenComparing(e -> e.row().getOrDefault("contact_group_id", ""))
                .thenComparing(e -> Long.parseLong(e.row().get("pet_id"))));
        return sorted;
    }

    static Plan plan(LocalDate registeredOn, List<Event> events) {
        List<Event> sorted = new ArrayList<>(events);
        // 같은 시각이면 방문 먼저: 방문으로 연 다음 주기에 그 뒤 연락이 붙는다
        sorted.sort(Comparator.comparing(Event::at).thenComparing(Event::kind));
        List<Event> before = new ArrayList<>();
        List<Event> after = new ArrayList<>();
        int skipped = 0;
        for (Event e : sorted) {
            if (!e.at().toLocalDate().isBefore(registeredOn)) {
                after.add(e);
            } else if (e.kind() == Kind.VISIT) {
                before.add(e);
            } else {
                skipped++;
            }
        }
        return new Plan(before, after, skipped);
    }
}
