package com.dadayiksun.crm.common.response;

/**
 * 성공 응답 {@code { "data": ... }} (C1, API 설계 1-2). 목록도 data 안에 {@link PageResponse}로 넣는다.
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
