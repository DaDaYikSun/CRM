package com.dadayiksun.crm.contact.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 재방문 전환 판정 (FR-CNT-004, POL-MISS-005~007, DB 설계 5-3). 순수 함수.
 * '방문 기록됨' 리스너와 더미 적재기(#147)가 같이 쓴다 (PR #187 리뷰).
 *
 * <ul>
 *   <li>방문이 완료시킨 주기의 연락만 본다. 연락은 예정 항목(주기) 단위라 '연락한 서비스로 왔을 때만'이 저절로 지켜진다 (MISS-007)</li>
 *   <li>방문일이 예정일 + 그 주기의 누락 기준(복사된 값) 안이어야 한다 (MISS-005). 기본 14일이면 14일째는 전환, 15일째는 아님</li>
 *   <li>방문 날 이전(같은 날 포함)에 남긴 연락 중 가장 늦은 1건만 전환이다 (MISS-006). 방문을 늦게 입력해도 방문 뒤 연락은 세지 않는다</li>
 * </ul>
 */
public final class ConversionRule {

    private ConversionRule() {
    }

    public record Contact(long contactLogId, LocalDateTime contactedAt) {
    }

    /** 전환으로 셀 연락 ID. 없으면 빈 값 */
    public static Optional<Long> convertedContact(List<Contact> contactsOfSchedule, LocalDate dueDate,
                                                 int missThresholdDays, LocalDate visitDate) {
        if (visitDate.isAfter(dueDate.plusDays(missThresholdDays))) {
            return Optional.empty();
        }
        return contactsOfSchedule.stream()
                .filter(c -> !c.contactedAt().toLocalDate().isAfter(visitDate))
                .max(Comparator.comparing(Contact::contactedAt).thenComparing(Contact::contactLogId))
                .map(Contact::contactLogId);
    }
}
