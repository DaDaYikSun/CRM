package com.dadayiksun.crm.target.dto;

/**
 * 탭별 아이 수 · 처리한 아이 수 (API 설계 3-6 {@code GET /api/targets/summary}, POL-MISS-009).
 * 숫자는 모두 아이 수이고 각 탭에 나오는 아이 수와 같다 (FR-DASH-001). 대시보드 카드(#156)도 그대로 쓴다.
 */
public record TargetSummaryResponse(Count today, Count week, long missed) {

    /** {@code done}: 그 탭 아이 중 오늘 연락 결과를 남긴 아이 수 */
    public record Count(long total, long done) {
    }
}
