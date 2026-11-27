package com.dadayiksun.crm.target.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dadayiksun.crm.target.domain.TargetCandidate.ServiceType;
import com.dadayiksun.crm.target.dto.TargetLineResponse;

/**
 * 한 탭에 들어간 예정 항목을 보호자 줄로 묶고 정렬한다 (POL-TGT-001 · 010 · 011).
 *
 * <ul>
 *   <li>한 줄 = 보호자 한 명. 아이마다 가장 가까운 접종 하나와 미용을 보이고, 나머지 접종은 펼치기로 (FR-TGT-002)</li>
 *   <li>줄 순서: 예정일이 오래 지난 순 (가장 이른 예정일 기준). 같으면 보호자 번호 순</li>
 *   <li>줄의 모든 항목에 오늘 결과를 남겼으면 '처리됨' 줄로 보고 맨 아래에 둔다 (POL-TGT-010) [제안: 일부만 남긴 줄은 위에 둠]</li>
 * </ul>
 */
public final class TargetLineAssembler {

    private static final Comparator<TargetCandidate> NEAREST =
            Comparator.comparing(TargetCandidate::dueDate)
                    .thenComparingInt(TargetCandidate::vaccineSortOrder)
                    .thenComparingLong(TargetCandidate::scheduleId);

    private TargetLineAssembler() {
    }

    public static List<TargetLineResponse> assemble(List<TargetCandidate> rows) {
        Map<Long, List<TargetCandidate>> byGuardian = new LinkedHashMap<>();
        for (TargetCandidate row : rows) {
            byGuardian.computeIfAbsent(row.guardianId(), k -> new ArrayList<>()).add(row);
        }
        List<Line> lines = new ArrayList<>();
        for (List<TargetCandidate> guardianRows : byGuardian.values()) {
            lines.add(new Line(guardianRows));
        }
        lines.sort(Comparator.comparing(Line::processed)
                .thenComparing(Line::earliestDue)
                .thenComparingLong(Line::guardianId));
        return lines.stream().map(Line::toResponse).toList();
    }

    private record Line(List<TargetCandidate> rows) {

        long guardianId() {
            return rows.get(0).guardianId();
        }

        LocalDate earliestDue() {
            return rows.stream().map(TargetCandidate::dueDate).min(Comparator.naturalOrder()).orElseThrow();
        }

        boolean processed() {
            return rows.stream().allMatch(TargetCandidate::processedToday);
        }

        TargetLineResponse toResponse() {
            TargetCandidate first = rows.get(0);
            Map<Long, List<TargetCandidate>> byPet = new LinkedHashMap<>();
            rows.stream().sorted(NEAREST).forEach(r -> byPet.computeIfAbsent(r.petId(), k -> new ArrayList<>()).add(r));
            List<TargetLineResponse.Pet> pets = byPet.values().stream().map(Line::toPet).toList();
            return new TargetLineResponse(first.guardianId(), first.guardianName(), first.phoneLast4(),
                    null, null, null, null, null, pets);
        }

        /** 이미 가까운 순으로 정렬된 한 아이의 항목들 */
        private static TargetLineResponse.Pet toPet(List<TargetCandidate> petRows) {
            List<TargetLineResponse.Item> items = new ArrayList<>();
            List<TargetLineResponse.Item> more = new ArrayList<>();
            boolean nearestVaccineTaken = false;
            for (TargetCandidate r : petRows) {
                if (r.serviceType() == ServiceType.GROOMING) {
                    items.add(item(r));
                } else if (!nearestVaccineTaken) {
                    items.add(item(r));
                    nearestVaccineTaken = true;
                } else {
                    more.add(item(r));
                }
            }
            TargetCandidate first = petRows.get(0);
            return new TargetLineResponse.Pet(first.petId(), first.petName(), items, more);
        }

        private static TargetLineResponse.Item item(TargetCandidate r) {
            return new TargetLineResponse.Item(r.scheduleId(), r.serviceType().name(), r.vaccineTypeName(),
                    r.dueDate(), r.missed());
        }
    }
}
