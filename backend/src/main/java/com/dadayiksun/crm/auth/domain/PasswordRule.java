package com.dadayiksun.crm.auth.domain;

import java.security.SecureRandom;

/**
 * 비밀번호 조건 (POL-ACC-010, 정책 4-2 '비밀번호 조건'): 8자 이상, 영문과 숫자를 섞는다.
 * 임시 비밀번호도 이 조건을 지켜 만든다 (#150).
 */
public final class PasswordRule {

    public static final int MIN_LENGTH = 8;
    /** 임시 비밀번호 길이 [제안]. 조건(8자 이상)보다 조금 길게. */
    static final int TEMP_LENGTH = 10;

    // 헷갈리는 글자(0 · O · o, 1 · l · I)는 뺀다. 직원에게 말로 전달하기 쉽게 [제안]
    private static final String LETTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordRule() {
    }

    public static boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }
        boolean letter = password.chars().anyMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
        boolean digit = password.chars().anyMatch(c -> c >= '0' && c <= '9');
        return letter && digit;
    }

    /** 조건을 지키는 임시 비밀번호. 영문 하나 · 숫자 하나는 꼭 들어간다. */
    public static String newTemporaryPassword() {
        String all = LETTERS + DIGITS;
        char[] chars = new char[TEMP_LENGTH];
        chars[0] = LETTERS.charAt(RANDOM.nextInt(LETTERS.length()));
        chars[1] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
        for (int i = 2; i < chars.length; i++) {
            chars[i] = all.charAt(RANDOM.nextInt(all.length()));
        }
        for (int i = chars.length - 1; i > 0; i--) { // 섞어서 앞 두 글자 자리를 감춘다
            int j = RANDOM.nextInt(i + 1);
            char t = chars[i];
            chars[i] = chars[j];
            chars[j] = t;
        }
        return new String(chars);
    }
}
