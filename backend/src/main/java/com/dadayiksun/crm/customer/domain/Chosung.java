package com.dadayiksun.crm.customer.domain;

/**
 * 이름 초성 ('김보호' → 'ㄱㅂㅎ'). 저장할 때 서버가 guardian.name_chosung · pet.name_chosung을 채운다
 * (DB 설계 4-2 · 4-3, FR-SRCH-003). 초성으로 찾는 검색은 초안 22가 한다.
 *
 * <p>한글 음절만 초성으로 바꾸고, 그 밖의 글자(영문 · 숫자 · 띄어쓰기 · 이미 초성인 글자)는 그대로 둔다.
 */
public final class Chosung {

    private static final char[] INITIALS = {
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };
    private static final char FIRST_SYLLABLE = '가';
    private static final char LAST_SYLLABLE = '힣';
    /** 초성 하나에 붙는 음절 수 (중성 21 × 종성 28). */
    private static final int SYLLABLES_PER_INITIAL = 21 * 28;

    private Chosung() {
    }

    public static String of(String name) {
        StringBuilder out = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            if (c >= FIRST_SYLLABLE && c <= LAST_SYLLABLE) {
                out.append(INITIALS[(c - FIRST_SYLLABLE) / SYLLABLES_PER_INITIAL]);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
