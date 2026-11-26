package com.dadayiksun.crm.customer.domain;

import java.util.regex.Pattern;

/**
 * 보호자 전화번호. 하이픈 · 띄어쓰기를 지우고 숫자만 저장 · 비교한다 (API 설계 1-4, DB 설계 1장).
 * 저장할 수 있는 모양은 DB 제약 {@code ck_guardian_phone}(숫자 9~11자리)과 같다.
 */
public final class PhoneNumber {

    private static final Pattern SEPARATORS = Pattern.compile("[-\\s]");
    private static final Pattern STORABLE = Pattern.compile("^[0-9]{9,11}$");

    private PhoneNumber() {
    }

    /** 하이픈 · 띄어쓰기를 지운 값. 다른 글자는 남겨 {@link #isStorable}에서 걸리게 한다. */
    public static String normalize(String raw) {
        return raw == null ? "" : SEPARATORS.matcher(raw).replaceAll("");
    }

    public static boolean isStorable(String normalized) {
        return STORABLE.matcher(normalized).matches();
    }
}
