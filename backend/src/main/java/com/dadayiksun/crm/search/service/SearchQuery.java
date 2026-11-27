package com.dadayiksun.crm.search.service;

/**
 * 검색어 정리 (FR-SRCH-001, API 설계 3-4). 띄어쓰기 · 하이픈을 지우고, 숫자만 남으면 전화번호, 아니면 이름 앞부분.
 * 초성 자음(ㄱ-ㅎ)만 있으면 초성 칸 앞부분으로 찾는다 (FR-SRCH-003).
 * 초성과 글자가 섞이면 이름 앞부분으로 찾는다 (임시 결정, #172 본문에 기준 없음).
 */
public record SearchQuery(String text, boolean phone, boolean chosung) {

    /** 이름 칸 길이(30)보다 긴 검색어는 의미가 없어 자른다 */
    private static final int MAX_LENGTH = 30;

    public static SearchQuery parse(String raw) {
        String text = raw == null ? "" : raw.replaceAll("[\\s-]", "");
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH);
        }
        return new SearchQuery(text, text.matches("\\d+"), text.matches("[ㄱ-ㅎ]+"));
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }
}
