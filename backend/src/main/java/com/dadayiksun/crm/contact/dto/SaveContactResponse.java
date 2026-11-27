package com.dadayiksun.crm.contact.dto;

/**
 * {@code POST /api/contacts} 출력 201 (API 설계 3-6).
 *
 * @param followUp 결과별 후속 처리 결과. #154가 채운다. 그 전에는 늘 null
 */
public record SaveContactResponse(String contactGroupId, Object followUp) {
}
