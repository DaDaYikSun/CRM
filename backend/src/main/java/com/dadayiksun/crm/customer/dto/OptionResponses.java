package com.dadayiksun.crm.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 선택지 출력 (API 설계 3-2 C5). */
public final class OptionResponses {

    private OptionResponses() {
    }

    /** 품종. defaultCoatTypeId가 있으면 화면이 털 유형을 미리 고른다 (DB 4-4). 없으면 null. */
    public record BreedOption(Long breedId, String name, Long defaultCoatTypeId) {
    }

    public record CoatTypeOption(Long coatTypeId, String code, String name) {
    }

    /** 읽기 전용 기준값 (API 설계 3-2 {@code GET /api/options/settings}). 비밀값이 아닌 것만. */
    public record SettingsResponse(int missThresholdDays, int rainbowHoldDays) {
    }

    public record VaccineTypeOption(Long vaccineTypeId, String code, String name,
            @JsonProperty("isMajor") boolean isMajor) {
    }
}
