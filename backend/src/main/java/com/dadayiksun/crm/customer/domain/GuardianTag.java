package com.dadayiksun.crm.customer.domain;

/** 주의 태그 4가지 고정값 (POL-DATA-009, API 설계 1-1). 코드는 JSON 배열의 값이고 DB의 tag_* 칸 4개와 1:1이다. */
public enum GuardianTag {
    NO_CONTACT, SMS_ONLY, SENSITIVE, CONTACT_TIME
}
