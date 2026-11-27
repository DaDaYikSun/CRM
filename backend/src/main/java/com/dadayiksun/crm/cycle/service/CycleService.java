package com.dadayiksun.crm.cycle.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dadayiksun.crm.common.setting.SettingKey;
import com.dadayiksun.crm.common.setting.SettingService;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.cycle.domain.MissJudgement;
import com.dadayiksun.crm.cycle.domain.PetProfile;
import com.dadayiksun.crm.cycle.domain.Schedule;
import com.dadayiksun.crm.cycle.domain.SchedulePlanner;
import com.dadayiksun.crm.cycle.domain.ServiceType;
import com.dadayiksun.crm.cycle.domain.VaccineRule;
import com.dadayiksun.crm.cycle.domain.VisitInfo;
import com.dadayiksun.crm.cycle.repository.CycleSourceQuery;
import com.dadayiksun.crm.cycle.repository.ScheduleRepository;

/**
 * C4 예정일 계산 · 다시 계산 · 방문 완료 (FR-CYC-001~004, DB 설계 5-1).
 * 부르는 쪽의 트랜잭션 안에서 돈다 (이벤트는 같은 트랜잭션에서 듣는다, API 설계 4-1).
 *
 * <ul>
 *   <li>새 주기를 만들 때 그때의 누락 기준(MISS_THRESHOLD_DAYS)을 행에 복사한다. 설정을 바꿔도 이미 열린 주기는 그대로 (POL-MISS-001)</li>
 *   <li>다시 계산은 예정일이 오늘 이후 · 당일인 OPEN 행만 고친다. 지난 행 · 누락 판정 · 완료 행은 그대로 (POL-CYC-005)</li>
 *   <li>규칙이 없어진 접종 종류 · 무지개다리 아이의 아직 오지 않은 OPEN 행은 VOID (DB 설계 5-1)</li>
 * </ul>
 */
@Service
@Transactional
public class CycleService {

    private final ScheduleRepository schedules;
    private final CycleSourceQuery source;
    private final SettingService settings;
    private final KstClock clock;

    public CycleService(ScheduleRepository schedules, CycleSourceQuery source, SettingService settings, KstClock clock) {
        this.schedules = schedules;
        this.source = source;
        this.settings = settings;
        this.clock = clock;
    }

    /** '아이 등록됨' 뒤 첫 예정 일정 (접종 종류마다 + 미용) */
    public void createInitialSchedules(long petId) {
        source.petProfile(petId).filter(PetProfile::active).ifPresent(pet -> plan(pet, false));
    }

    /**
     * '방문 기록됨' 뒤: 같은 아이 · 서비스(· 종류)의 OPEN 주기를 완료하고 다음 주기를 연다.
     * 완료 때 missed_at은 K20 · T16: 기준 안이면 지우고, 기준 밖인데 비었으면 원래 판정일로 채운다.
     */
    public VisitOutcome onVisitRecorded(long visitId) {
        VisitInfo visit = source.visit(visitId)
                .orElseThrow(() -> new IllegalStateException("방문 기록이 없다: " + visitId));
        Optional<Schedule> open = schedules.findOpen(visit.petId(), visit.serviceType(), visit.vaccineTypeId());
        Long completed = null;
        if (open.isPresent()) {
            Schedule s = open.get();
            LocalDate missedAt = MissJudgement.missedAtOnCompletion(s.dueDate(), s.missThresholdDays(),
                    visit.visitDate(), s.missedAt()).orElse(null);
            schedules.complete(s.scheduleId(), visitId, visit.visitDate(),
                    MissJudgement.gapDays(s.dueDate(), visit.visitDate()), missedAt);
            completed = s.scheduleId();
        }
        source.petProfile(visit.petId()).filter(PetProfile::active).ifPresent(pet -> plan(pet, false));
        Optional<Schedule> next = schedules.findOpen(visit.petId(), visit.serviceType(), visit.vaccineTypeId());
        return new VisitOutcome(completed, next.map(Schedule::scheduleId).orElse(null));
    }

    /** '프로필 바뀜' 뒤: 아직 오지 않은 예정일만 다시 계산 (POL-CYC-005) */
    public void recalculatePet(long petId) {
        Optional<PetProfile> profile = source.petProfile(petId);
        if (profile.isEmpty()) {
            return;
        }
        PetProfile pet = profile.get();
        if (!pet.active()) {
            LocalDate today = clock.today();
            schedules.findOpenByPet(petId).stream()
                    .filter(s -> !s.dueDate().isBefore(today))
                    .forEach(s -> schedules.voidSchedule(s.scheduleId()));
            return;
        }
        plan(pet, true);
    }

    /** 규칙 저장 뒤: 쓰는 아이 모두 다시 계산 (#144 남은 질문 2: 저장 요청 안에서 한다, 400마리 규모) */
    public void recalculateByRule() {
        source.activePetIds().forEach(this::recalculatePet);
    }

    /**
     * 열린 주기가 없는 종류는 새로 열고, recalc이면 아직 오지 않은 OPEN 행의 예정일을 고친다.
     */
    private void plan(PetProfile pet, boolean recalc) {
        LocalDate today = clock.today();
        int threshold = settings.getInt(SettingKey.MISS_THRESHOLD_DAYS);
        Map<Long, List<VaccineRule>> rulesByType = source.activeRulesByType();

        for (Map.Entry<Long, List<VaccineRule>> e : rulesByType.entrySet()) {
            Long typeId = e.getKey();
            Optional<LocalDate> due = SchedulePlanner.nextVaccineDue(pet, e.getValue(),
                    source.visitDates(pet.petId(), ServiceType.VACCINE, typeId));
            apply(pet.petId(), ServiceType.VACCINE, typeId, due, threshold, today, recalc);
        }
        if (recalc) {
            // 규칙이 없어진(또는 쓰지 않는) 접종 종류의 아직 오지 않은 주기
            schedules.findOpenByPet(pet.petId()).stream()
                    .filter(s -> s.serviceType() == ServiceType.VACCINE && !rulesByType.containsKey(s.vaccineTypeId()))
                    .filter(s -> !s.dueDate().isBefore(today))
                    .forEach(s -> schedules.voidSchedule(s.scheduleId()));
        }

        SchedulePlanner.GroomPlan groom = SchedulePlanner.nextGroom(pet,
                source.visitDates(pet.petId(), ServiceType.GROOMING, null));
        schedules.saveGroomInterval(pet.petId(), groom.interval());
        apply(pet.petId(), ServiceType.GROOMING, null, Optional.of(groom.dueDate()), threshold, today, recalc);
    }

    private void apply(long petId, ServiceType service, Long typeId, Optional<LocalDate> due, int threshold,
                       LocalDate today, boolean recalc) {
        Optional<Schedule> open = schedules.findOpen(petId, service, typeId);
        if (open.isEmpty()) {
            due.ifPresent(d -> schedules.insertOpen(petId, service, typeId, d, threshold));
            return;
        }
        Schedule s = open.get();
        if (!recalc || s.dueDate().isBefore(today)) {
            return; // 지난 예정일 · 누락 판정은 그대로 (POL-CYC-005)
        }
        if (due.isEmpty()) {
            schedules.voidSchedule(s.scheduleId());
        } else if (!due.get().equals(s.dueDate())) {
            schedules.updateDueDate(s.scheduleId(), due.get());
        }
    }

    /** 방문 저장 출력의 completedScheduleId · nextSchedule 조립용 (API 설계 3-5) */
    public record VisitOutcome(Long completedScheduleId, Long nextScheduleId) {
    }
}
