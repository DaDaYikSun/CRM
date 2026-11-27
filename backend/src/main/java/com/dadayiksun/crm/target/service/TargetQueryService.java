package com.dadayiksun.crm.target.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.response.PageResponse;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.common.web.PageQuery;
import com.dadayiksun.crm.target.domain.TargetCandidate;
import com.dadayiksun.crm.target.domain.TargetLineAssembler;
import com.dadayiksun.crm.target.domain.TargetRules;
import com.dadayiksun.crm.target.domain.TargetTab;
import com.dadayiksun.crm.target.dto.TargetLineResponse;
import com.dadayiksun.crm.target.dto.TargetSummaryResponse;
import com.dadayiksun.crm.target.repository.TargetCandidateQuery;

/**
 * 연락 대상 목록 · 요약 (C11, FR-TGT-001~003). 읽기만 한다 (K17). '오늘' · '이번 주'는 한국 시간 (POL-TGT-001).
 * 목록과 요약은 같은 판단({@link TargetRules})을 써서 탭 아이 수와 카드 숫자가 늘 같다 (FR-DASH-001).
 */
@Service
@Transactional(readOnly = true)
public class TargetQueryService {

    private final TargetCandidateQuery query;
    private final KstClock clock;

    public TargetQueryService(TargetCandidateQuery query, KstClock clock) {
        this.query = query;
        this.clock = clock;
    }

    /** 한 페이지 = 보호자 줄 20개 (NFR-PERF-002). 마지막 페이지보다 크면 빈 목록과 total (K16 ⑥) */
    public PageResponse<TargetLineResponse> list(TargetTab tab, PageQuery page) {
        List<TargetLineResponse> lines = TargetLineAssembler.assemble(rowsIn(tab, candidates()));
        long total = lines.size();
        List<TargetLineResponse> items = page.isBeyond(total)
                ? List.of()
                : lines.subList((int) page.offset(), (int) Math.min(total, page.offset() + page.limit()));
        return PageResponse.of(items, page, total);
    }

    public TargetSummaryResponse summary() {
        List<TargetCandidate> all = candidates();
        List<TargetCandidate> today = rowsIn(TargetTab.TODAY, all);
        List<TargetCandidate> week = rowsIn(TargetTab.WEEK, all);
        return new TargetSummaryResponse(count(today), count(week), pets(rowsIn(TargetTab.MISSED, all)));
    }

    private List<TargetCandidate> candidates() {
        return query.findOpenCandidates(clock.today(), clock.weekEnd());
    }

    private List<TargetCandidate> rowsIn(TargetTab tab, List<TargetCandidate> all) {
        LocalDate today = clock.today();
        LocalDate weekEnd = clock.weekEnd();
        return all.stream().filter(c -> TargetRules.belongs(c, tab, today, weekEnd)).toList();
    }

    private static TargetSummaryResponse.Count count(List<TargetCandidate> rows) {
        long done = rows.stream().filter(TargetCandidate::processedToday).map(TargetCandidate::petId).distinct().count();
        return new TargetSummaryResponse.Count(pets(rows), done);
    }

    private static long pets(List<TargetCandidate> rows) {
        return rows.stream().map(TargetCandidate::petId).distinct().count();
    }
}
