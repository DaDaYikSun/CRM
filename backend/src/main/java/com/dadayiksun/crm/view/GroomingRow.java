package com.dadayiksun.crm.view;

import java.time.LocalDate;
import java.util.List;

/**
 * 미용 목록 한 줄의 재료 ({@link GroomingListQuery}가 읽음). 저장된 값만 담는다 (K17 ②).
 * {@code groomedToday}면 오늘 미용으로 완료된 주기 값이고, 아니면 진행 중(OPEN) 미용 주기 값이다.
 * 오늘 미용 방문이 완료한 주기가 없으면(주기 없이 기록) {@code scheduleId} · {@code dueDate}가 null이다.
 */
public record GroomingRow(long petId, String petName, String breedName, String coatTypeName, LocalDate birthDate,
        String guardianName, String phoneLast4, List<String> guardianTags, Long scheduleId, LocalDate dueDate,
        boolean missed, LocalDate lastGroomDate, Integer groomIntervalDays, String groomIntervalBasis,
        boolean groomedToday) {
}
