package com.dadayiksun.crm.cycle.domain;

import java.time.LocalDate;

/**
 * 예정일 계산에 필요한 아이 값 (pet · coat_type에서 읽기만 함).
 *
 * @param active          무지개다리가 아니면 true (무지개다리 아이는 계산 제외)
 * @param coatDefaultDays 털 유형 기본 미용 간격(일) = coat_type.default_groom_weeks × 7 (POL-CYC-002)
 * @param registeredOn    등록일(한국 날짜). 미용 기록이 없을 때 첫 미용 예정일의 기준 (#144 결정)
 */
public record PetProfile(long petId, LocalDate birthDate, boolean active, int coatDefaultDays, LocalDate registeredOn) {
}
