package com.dadayiksun.crm.cycle.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *   <li>다시 계산은 예정일이 오늘 이후 · 당일인 OPEN 행만 고친다. 지난 행 · 누락 판정 · 완료 행은 그대로 (POL-CYC-005).
 *       다시 계산한 예정일이 지난 날짜면 오늘로 올린다 (PR #202 리뷰 결정)</li>
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
        source.petProfile(petId).filter(PetProfile::active).ifPresent(pet -> plan(pet, false, null));
    }

    /**
     * '방문 기록됨' 뒤: 같은 아이 · 서비스(· 종류)의 OPEN 주기를 완료하고 다음 주기를 연다.
     * 완료 때 missed_at은 K20 · T16: 기준 안이면 지우고, 기준 밖인데 비었으면 원래 판정일로 채운다.
     */
    public VisitOutcome onVisitRecorded(long visitId) {
        VisitInfo visit = source.visit(visitId)
                .orElseThrow(() -> new IllegalStateException("방문 기록이 없다: " + visitId));
        List<Schedule> history = schedules.findByPet(visit.petId()).stream()
                .filter(s -> s.serviceType() == visit.serviceType() && Objects.equals(s.vaccineTypeId(), visit.vaccineTypeId()))
                .toList();

        // 같은 방문으로 다시 불리면 아무것도 하지 않는다. 그 방문이 완료한 주기(done_visit_id)나
        // 그 방문이 연 주기(opened_by_visit_id, V3)가 있으면 이미 처리했다 (열린 주기가 없던 방문도 알아본다, PR #202 리뷰)
        Optional<Schedule> done = history.stream().filter(s -> Objects.equals(s.doneVisitId(), visitId)).findFirst();
        if (done.isPresent()) {
            return new VisitOutcome(done.get().scheduleId(), openId(visit));
        }
        if (history.stream().anyMatch(s -> Objects.equals(s.openedByVisitId(), visitId))) {
            return new VisitOutcome(null, openId(visit));
        }
        // 방문일이 이미 완료된 주기의 완료일 이하이면 그 주기에 속한 늦은 입력 · 같은 날 두 번 입력이다.
        // 다음 주기는 건드리지 않고, 그 주기의 기준 안이면 저장한 판정만 지운다 (T16, DB 설계 5-1)
        Optional<Schedule> belongsTo = history.stream()
                .filter(s -> s.status() == Schedule.Status.DONE && !visit.visitDate().isAfter(s.doneDate()))
                .min(Comparator.comparing(Schedule::doneDate));
        if (belongsTo.isPresent()) {
            Schedule s = belongsTo.get();
            if (s.missedAt() != null && MissJudgement.isWithinThreshold(visit.visitDate(), s.dueDate(), s.missThresholdDays())) {
                schedules.clearMissedAt(s.scheduleId());
            }
            // 늦게 들어온 접종 기록으로 횟수가 바뀌면 다음 예정일도 바뀐다: 아직 오지 않은 예정일만 다시 계산 (FR-CYC-003)
            recalculatePet(visit.petId());
            return new VisitOutcome(null, openId(visit));
        }
        // 열린 주기가 없을 때 들어온 방문이 연 주기도 같다: 그 방문일 이하의 다른 방문(같은 날 두 번 · 늦은 입력)은
        // 방금 연 주기를 닫지 않고 아직 오지 않은 예정일만 다시 계산한다 (PR #202 리뷰)
        boolean beforeOpener = history.stream()
                .filter(s -> s.status() == Schedule.Status.OPEN && s.openedByVisitId() != null)
                .flatMap(s -> source.visit(s.openedByVisitId()).stream())
                .anyMatch(opener -> !visit.visitDate().isAfter(opener.visitDate()));
        if (beforeOpener) {
            recalculatePet(visit.petId());
            return new VisitOutcome(null, openId(visit));
        }

        Optional<Schedule> open = schedules.findOpen(visit.petId(), visit.serviceType(), visit.vaccineTypeId());
        Long completed = null;
        if (open.isPresent()) {
            Schedule s = open.get();
            LocalDate missedAt = MissJudgement.missedAtOnCompletion(s.dueDate(), s.missThresholdDays(),
                    visit.visitDate(), s.missedAt()).orElse(null);
            // 동시에 다른 방문이 먼저 완료했으면 바뀐 행이 없다. 이 방문이 완료한 것으로 돌려주지 않는다 (PR #202 리뷰)
            if (schedules.complete(s.scheduleId(), visitId, visit.visitDate(),
                    MissJudgement.gapDays(s.dueDate(), visit.visitDate()), missedAt)) {
                completed = s.scheduleId();
            }
        }
        source.petProfile(visit.petId()).filter(PetProfile::active).ifPresent(pet -> plan(pet, false, visit));
        return new VisitOutcome(completed, openId(visit));
    }

    private Long openId(VisitInfo visit) {
        return schedules.findOpen(visit.petId(), visit.serviceType(), visit.vaccineTypeId())
                .map(Schedule::scheduleId).orElse(null);
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
        plan(pet, true, null);
    }

    /** 규칙 저장 뒤: 쓰는 아이 모두 다시 계산 (#144 남은 질문 2: 저장 요청 안에서 한다, 400마리 규모) */
    public void recalculateByRule() {
        source.activePetIds().forEach(this::recalculatePet);
    }

    /**
     * 열린 주기가 없는 종류는 새로 열고, recalc이면 아직 오지 않은 OPEN 행의 예정일을 고친다.
     * visit이 있으면 그 방문의 서비스 · 종류로 새로 여는 주기에 opened_by_visit_id를 남긴다.
     */
    private void plan(PetProfile pet, boolean recalc, VisitInfo visit) {
        LocalDate today = clock.today();
        int threshold = settings.getInt(SettingKey.MISS_THRESHOLD_DAYS);
        Map<Long, List<VaccineRule>> rulesByType = source.activeRulesByType();

        for (Map.Entry<Long, List<VaccineRule>> e : rulesByType.entrySet()) {
            Long typeId = e.getKey();
            Optional<LocalDate> due = SchedulePlanner.nextVaccineDue(pet, e.getValue(),
                    source.visitDates(pet.petId(), ServiceType.VACCINE, typeId));
            apply(pet.petId(), ServiceType.VACCINE, typeId, due, threshold, today, recalc, openerOf(visit, ServiceType.VACCINE, typeId));
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
        apply(pet.petId(), ServiceType.GROOMING, null, Optional.of(groom.dueDate()), threshold, today, recalc,
                openerOf(visit, ServiceType.GROOMING, null));
    }

    private static Long openerOf(VisitInfo visit, ServiceType service, Long typeId) {
        return visit != null && visit.serviceType() == service && Objects.equals(visit.vaccineTypeId(), typeId)
                ? visit.visitId() : null;
    }

    private void apply(long petId, ServiceType service, Long typeId, Optional<LocalDate> computed, int threshold,
                       LocalDate today, boolean recalc, Long openedByVisitId) {
        // 다시 계산이 지난 날짜를 내면 오늘로 올린다. 다시 계산으로 지나간 누락이 생기지 않게 (POL-CYC-005, PR #202 리뷰 결정)
        Optional<LocalDate> due = recalc ? computed.map(d -> d.isBefore(today) ? today : d) : computed;
        Optional<Schedule> open = schedules.findOpen(petId, service, typeId);
        if (open.isEmpty()) {
            due.ifPresent(d -> schedules.insertOpen(petId, service, typeId, d, threshold, openedByVisitId));
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
