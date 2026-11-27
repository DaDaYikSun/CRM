package com.dadayiksun.crm.contact.domain;

import java.util.Arrays;
import java.util.Optional;

/** 연락 결과 5가지 (POL-CNT-002, FR-CNT-002). 이 밖의 값은 저장하지 않는다 (NFR-DATA-003). 글자는 정책 그대로. */
public enum ContactResult {
    BOOKED("예약함"),
    NO_ANSWER("전화 안 받음"),
    REFUSED("거절"),
    OTHER_HOSPITAL("다른 병원 다님"),
    WRONG_NUMBER("번호 오류");

    private final String label;

    ContactResult(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<ContactResult> parse(String raw) {
        return Arrays.stream(values()).filter(r -> r.name().equals(raw)).findFirst();
    }
}
