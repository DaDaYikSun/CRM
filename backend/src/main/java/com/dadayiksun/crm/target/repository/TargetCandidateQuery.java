package com.dadayiksun.crm.target.repository;

import java.time.LocalDate;
import java.util.List;

import com.dadayiksun.crm.target.domain.TargetCandidate;

/**
 * 연락 대상 후보를 읽는 조회 전용 쿼리 (K17 ②, DB 설계 5-2).
 * 다른 도메인 테이블을 읽기만 한다: schedule(cycle), pet · guardian(customer), vaccine_type(cycle 규칙), contact_log(contact).
 */
public interface TargetCandidateQuery {

    /**
     * 진행 중(OPEN) 예정 일정 중 이번 주 일요일까지 예정이거나 누락 판정이 저장된 행.
     * 탭별 판단은 {@link com.dadayiksun.crm.target.domain.TargetRules}가 한다.
     *
     * @param today   한국 시간 오늘 (처리됨 판단)
     * @param weekEnd 한국 시간 이번 주 일요일
     */
    List<TargetCandidate> findOpenCandidates(LocalDate today, LocalDate weekEnd);
}
