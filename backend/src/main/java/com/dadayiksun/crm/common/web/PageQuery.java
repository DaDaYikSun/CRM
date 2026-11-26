package com.dadayiksun.crm.common.web;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;

import java.util.List;

/**
 * 목록 페이지 규칙 (K16 ⑥, API 설계 1-3).
 * 한 번에 20개 고정, size는 받지 않는다. page는 1부터, 없으면 1.
 * 1보다 작거나 숫자가 아니면 400 VALIDATION_FAILED. 마지막보다 크면 오류 없이 빈 items와 total.
 *
 * <p>쓰는 법: {@code PageQuery q = PageQuery.parse(pageParam); long total = count(); 
 * List<T> items = q.isBeyond(total) ? List.of() : find(q.limit(), q.offset());}
 * Controller에서 page를 String으로 받아 넘긴다 (숫자가 아닌 값도 이 규칙으로 막기 위해서).
 */
public record PageQuery(int page) {

    public static final int SIZE = 20;

    public static PageQuery parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new PageQuery(1);
        }
        int page;
        try {
            page = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (page < 1) {
            throw invalid();
        }
        return new PageQuery(page);
    }

    public int limit() {
        return SIZE;
    }

    public long offset() {
        return (long) (page - 1) * SIZE;
    }

    /** 이 페이지가 마지막 페이지보다 뒤면 true. 이때는 쿼리를 하지 않고 빈 items를 준다. */
    public boolean isBeyond(long total) {
        return offset() >= total;
    }

    private static ApiException invalid() {
        String message = ErrorCode.VALIDATION_FALLBACK_MESSAGE;
        return new ApiException(ErrorCode.VALIDATION_FAILED, message,
                List.of(new ErrorResponse.FieldError("page", message)));
    }
}
