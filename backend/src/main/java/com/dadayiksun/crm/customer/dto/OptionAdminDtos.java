package com.dadayiksun.crm.customer.dto;

/**
 * B-03 선택지 관리 입출력 (FR-ADM-005, API 설계 3-10 '선택지 관리 · 변경 이력', #182).
 * 목록은 숨긴 것도 준다. 입력은 비었을 수 있어 감싼 타입이다. 검사는 {@code OptionAdminService}가 한다.
 */
public final class OptionAdminDtos {

    private OptionAdminDtos() {
    }

    public record BreedItem(long breedId, String name, Long defaultCoatTypeId, int sortOrder, boolean isActive) {
    }

    /** POST · PATCH 입력. PATCH는 보낸 칸만 바꾼다. defaultCoatTypeId를 비우려면 clearDefaultCoatType = true */
    public record BreedInput(String name, Long defaultCoatTypeId, Boolean clearDefaultCoatType, Integer sortOrder,
                             Boolean isActive) {
    }

    public record CoatTypeItem(long coatTypeId, String code, String name, int defaultGroomWeeks, int sortOrder,
                               boolean isActive) {
    }

    /**
     * POST · PATCH 입력. 미용 기본 간격은 추가할 때만 받는다: 이미 쓰는 털 유형의 간격을 바꾸면 예정일을 다시 계산해야 해서
     * B-02 주기 규칙({@code PUT /api/admin/coat-types/intervals}, #175)에서만 바꾼다.
     */
    public record CoatTypeInput(String name, Integer defaultGroomWeeks, Integer sortOrder, Boolean isActive) {
    }
}
