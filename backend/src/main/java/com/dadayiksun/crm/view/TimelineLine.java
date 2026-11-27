package com.dadayiksun.crm.view;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 통합 타임라인 한 줄 (API 설계 3-5). 연락 줄만 {@code converted} · {@code mockSms}가 있다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimelineLine(Kind kind, Long id, LocalDate date, String summary, String memo, String staffName,
        OffsetDateTime recordedAt, boolean edited, boolean editable, Boolean converted, Boolean mockSms) {

    public enum Kind {
        VACCINE, GROOMING, CONTACT
    }
}
