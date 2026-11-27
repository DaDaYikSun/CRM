package com.dadayiksun.crm.search.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.search.dto.SearchItem;
import com.dadayiksun.crm.search.repository.SearchRepository;

/** C7 검색 (API 설계 3-4). 읽기만 한다. */
@Service
@Transactional(readOnly = true)
public class SearchService {

    /** 미리보기 건수: 이 이슈에서 5건으로 정함 (API 설계 7장 8번에 반영 예정) */
    static final int PREVIEW_SIZE = 5;

    private final SearchRepository repository;

    public SearchService(SearchRepository repository) {
        this.repository = repository;
    }

    public List<SearchItem> preview(String raw) {
        SearchQuery q = SearchQuery.parse(raw);
        return q.isEmpty() ? List.of() : repository.find(q, PREVIEW_SIZE, 0);
    }

    public PageResponse<SearchItem> search(String raw, PageQuery page) {
        SearchQuery q = SearchQuery.parse(raw);
        long total = q.isEmpty() ? 0 : repository.count(q);
        List<SearchItem> items = page.isBeyond(total) ? List.of() : repository.find(q, page.limit(), page.offset());
        return PageResponse.of(items, page, total);
    }
}
