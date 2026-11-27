package com.dadayiksun.crm.cycle.service;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.cycle.repository.ScheduleRepository;

/**
 * C12: contact가 schedule의 연락 상태(contact_state · no_answer_count · next_contact_date)를 고칠 때 쓰는 cycle 인터페이스
 * (K17 ①, API 설계 4장). contact는 schedule을 직접 고치지 않는다. 연락 저장과 같은 트랜잭션에서 불린다 (DB 설계 4-10).
 * 어떤 결과에 어떤 값을 넣을지는 #154(결과별 후속 처리)가 정한다. 여기는 값만 저장한다.
 */
@Service
public class ScheduleContactUpdater {

    private static final Set<String> STATES =
            Set.of("NONE", "NO_ANSWER", "BOOKED", "REFUSED", "OTHER_HOSPITAL", "WRONG_NUMBER", "CLOSED");

    private final ScheduleRepository schedules;

    public ScheduleContactUpdater(ScheduleRepository schedules) {
        this.schedules = schedules;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(long scheduleId, String contactState, int noAnswerCount, LocalDate nextContactDate) {
        if (!STATES.contains(contactState)) {
            throw new IllegalArgumentException("정해진 연락 상태가 아니다: " + contactState);
        }
        if (noAnswerCount < 0) {
            throw new IllegalArgumentException("전화 안 받음 횟수는 0 이상이어야 한다");
        }
        schedules.findById(scheduleId)
                .orElseThrow(() -> new IllegalStateException("예정 일정이 없다: " + scheduleId));
        schedules.updateContact(scheduleId, contactState, noAnswerCount, nextContactDate);
    }
}
