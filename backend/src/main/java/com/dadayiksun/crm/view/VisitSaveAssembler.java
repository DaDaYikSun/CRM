package com.dadayiksun.crm.view;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.auth.domain.CurrentStaff;
import com.dadayiksun.crm.customer.dto.PetDetail;
import com.dadayiksun.crm.customer.service.PetDetailReader;
import com.dadayiksun.crm.cycle.domain.Schedule;
import com.dadayiksun.crm.cycle.service.CycleQueryService;
import com.dadayiksun.crm.visit.dto.RecordVisitRequest;
import com.dadayiksun.crm.visit.service.VisitService;

/**
 * 방문 저장 출력 조립 (C8, API 설계 3-5 · 4-1). visit이 저장하고 이벤트를 내면 cycle이 같은 트랜잭션에서 주기를 닫는다.
 * 그 뒤 cycle Reader(C4)로 닫힌 주기 · 다음 주기를, customer로 개인 미용 주기를 읽어 붙인다.
 * 응답은 트랜잭션 안에서 만든다: 커밋 뒤에 실패해 5xx가 나가지 않게 (#193).
 */
@Component
public class VisitSaveAssembler {

    private final VisitService visits;
    private final CycleQueryService cycle;
    private final PetDetailReader pets;

    public VisitSaveAssembler(VisitService visits, CycleQueryService cycle, PetDetailReader pets) {
        this.visits = visits;
        this.cycle = cycle;
        this.pets = pets;
    }

    @Transactional
    public Response record(long petId, RecordVisitRequest req, CurrentStaff staff) {
        long visitId = visits.record(petId, req, staff);
        List<Schedule> schedules = cycle.findSchedules(petId);
        Long completed = schedules.stream().filter(s -> Objects.equals(s.doneVisitId(), visitId))
                .map(Schedule::scheduleId).findFirst().orElse(null);
        NextSchedule next = schedules.stream()
                .filter(s -> s.status() == Schedule.Status.OPEN && s.serviceType().name().equals(req.serviceType())
                        && Objects.equals(s.vaccineTypeId(), req.vaccineTypeId()))
                .min(Comparator.comparing(Schedule::dueDate))
                .map(s -> new NextSchedule(s.scheduleId(), s.serviceType().name(), s.vaccineTypeId(), s.dueDate()))
                .orElse(null);
        PetDetail.GroomInterval groom = "GROOMING".equals(req.serviceType())
                ? pets.read(petId).pet().groomInterval() : null;
        return new Response(visitId, completed, next, groom);
    }

    public record Response(long visitId, Long completedScheduleId, NextSchedule nextSchedule,
                           PetDetail.GroomInterval groomInterval) {
    }

    public record NextSchedule(long scheduleId, String serviceType, Long vaccineTypeId, LocalDate dueDate) {
    }
}
