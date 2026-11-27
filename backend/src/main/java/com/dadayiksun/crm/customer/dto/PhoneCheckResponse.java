package com.dadayiksun.crm.customer.dto;

import java.util.List;

/**
 * {@code GET /api/guardians/phone-check} 출력 (API 설계 3-3 C6). 같은 번호 보호자가 여럿일 수 있어 배열이다.
 * 화면 문구: "이미 등록된 보호자입니다: {보호자 이름} (아이 {N}마리)" (정책 4-2).
 */
public record PhoneCheckResponse(boolean exists, List<Match> guardians) {

    public record Match(Long guardianId, String name, long petCount) {
    }

    public static PhoneCheckResponse of(List<Match> guardians) {
        return new PhoneCheckResponse(!guardians.isEmpty(), guardians);
    }
}
