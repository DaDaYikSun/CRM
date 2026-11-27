package com.dadayiksun.crm.cycle.service;

import static com.dadayiksun.crm.cycle.domain.VaccinePhase.BOOSTER;
import static com.dadayiksun.crm.cycle.domain.VaccinePhase.PRIMARY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dadayiksun.crm.cycle.domain.GroomIntervalBasis;
import com.dadayiksun.crm.cycle.domain.PetProfile;
import com.dadayiksun.crm.cycle.domain.Schedule;
import com.dadayiksun.crm.cycle.domain.ServiceType;
import com.dadayiksun.crm.cycle.domain.VaccineRule;
import com.dadayiksun.crm.cycle.domain.VisitInfo;

/** C4 흐름: 첫 예정 · 방문 완료(K20 · T16) · 다시 계산(POL-CYC-005) · 누락 판정 · 기준 복사(POL-MISS-001). */
class CycleServiceTest {

    static final long RABIES = 4L;
    static final List<VaccineRule> RABIES_RULES = List.of(
            new VaccineRule(PRIMARY, 90, 365, 365, 1),
            new VaccineRule(BOOSTER, 365, null, 365, null));
    static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    CycleFakes.Schedules schedules;
    CycleFakes.Source source;
    CycleService service;

    @BeforeEach
    void setUp() {
        schedules = new CycleFakes.Schedules();
        source = new CycleFakes.Source();
        source.rules.put(RABIES, RABIES_RULES);
        service = new CycleService(schedules, source, CycleFakes.settings(14), CycleFakes.clockAt(TODAY));
    }

    private Schedule open(long petId, ServiceType service, Long type) {
        return schedules.findOpen(petId, service, type).orElseThrow();
    }

    @Nested
    @DisplayName("아이 등록됨 → 첫 예정 일정 (DB 설계 5-1)")
    class Initial {

        @Test
        void 어린_강아지는_종류마다_첫_예정과_미용_예정이_생긴다() {
            // 2026-10-01생, 단모(70일), 오늘 등록
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2026, 10, 1), true, 70, TODAY));
            service.createInitialSchedules(1);

            assertThat(open(1, ServiceType.VACCINE, RABIES).dueDate()).isEqualTo(LocalDate.of(2026, 12, 30)); // +90일
            assertThat(open(1, ServiceType.GROOMING, null).dueDate()).isEqualTo(TODAY.plusDays(70));
            assertThat(schedules.groomIntervals.get(1L).basis()).isEqualTo(GroomIntervalBasis.DEFAULT);
        }

        @Test
        void 기록_없는_성견의_첫_접종_예정은_등록일이고_등록일보다_앞선_누락은_생기지_않는다() {
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2020, 1, 1), true, 70, TODAY));
            service.createInitialSchedules(1);
            assertThat(open(1, ServiceType.VACCINE, RABIES).dueDate()).isEqualTo(TODAY);

            // 등록 다음 날 판정해도 누락 없음, 기준을 넘기면 그때 누락이고 판정일은 등록일 뒤
            new MissJudgementJob(schedules, CycleFakes.clockAt(TODAY.plusDays(1))).run();
            assertThat(schedules.rows).allMatch(s -> s.missedAt() == null);
            new MissJudgementJob(schedules, CycleFakes.clockAt(TODAY.plusDays(16))).run();
            assertThat(schedules.rows).filteredOn(s -> s.missedAt() != null)
                    .isNotEmpty().allMatch(s -> !s.missedAt().isBefore(TODAY));
        }

        @Test
        void 기록이_있으면_등록일과_상관없이_규칙대로() {
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2020, 1, 1), true, 70, TODAY));
            source.visits.add(new VisitInfo(9, 1, ServiceType.VACCINE, RABIES, LocalDate.of(2020, 4, 1)));
            service.createInitialSchedules(1);
            assertThat(open(1, ServiceType.VACCINE, RABIES).dueDate()).isEqualTo(LocalDate.of(2021, 4, 1));
        }

        @Test
        void 등록_때_누락_기준을_주기에_복사한다() {
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2026, 10, 1), true, 70, TODAY));
            service.createInitialSchedules(1);
            assertThat(schedules.rows).allMatch(s -> s.missThresholdDays() == 14);
        }

        @Test
        void 무지개다리_아이는_만들지_않는다() {
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2026, 10, 1), false, 70, TODAY));
            service.createInitialSchedules(1);
            assertThat(schedules.rows).isEmpty();
        }

        @Test
        void 같은_아이_서비스에_OPEN이_두_개_생기지_않는다() {
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2026, 10, 1), true, 70, TODAY));
            service.createInitialSchedules(1);
            service.createInitialSchedules(1);
            assertThat(schedules.findOpenByPet(1)).hasSize(2);
        }
    }

    @Nested
    @DisplayName("방문 기록됨 → 완료 · 다음 주기 (K20, T16, POL-MISS-002)")
    class Visit {

        @BeforeEach
        void pet() {
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2025, 1, 1), true, 70, LocalDate.of(2025, 6, 1)));
        }

        private Schedule openWith(LocalDate due, LocalDate missedAt) {
            return schedules.put(new Schedule(1, 1, ServiceType.VACCINE, RABIES, due, Schedule.Status.OPEN, 14,
                    null, null, null, missedAt, "NONE", 0, null));
        }

        private CycleService.VisitOutcome visit(long id, LocalDate date) {
            source.visits.add(new VisitInfo(id, 1, ServiceType.VACCINE, RABIES, date));
            return service.onVisitRecorded(id);
        }

        @Test
        void 기준_안_방문은_완료하고_gap_days를_남기고_다음_주기를_연다() {
            openWith(LocalDate.of(2026, 11, 1), null);
            CycleService.VisitOutcome out = visit(10, LocalDate.of(2026, 11, 10));

            Schedule done = schedules.findById(1).orElseThrow();
            assertThat(done.status()).isEqualTo(Schedule.Status.DONE);
            assertThat(done.gapDays()).isEqualTo(9);
            assertThat(done.missedAt()).isNull();
            assertThat(out.completedScheduleId()).isEqualTo(1L);
            // 1회 채움 → 추가접종 365일 뒤
            assertThat(open(1, ServiceType.VACCINE, RABIES).dueDate()).isEqualTo(LocalDate.of(2027, 11, 10));
            assertThat(out.nextScheduleId()).isEqualTo(open(1, ServiceType.VACCINE, RABIES).scheduleId());
        }

        @Test
        void 판정_전_기준_밖_방문은_원래_판정일을_채운다() {
            openWith(LocalDate.of(2026, 11, 1), null);
            visit(10, LocalDate.of(2026, 11, 20));
            assertThat(schedules.findById(1).orElseThrow().missedAt()).isEqualTo(LocalDate.of(2026, 11, 16));
        }

        @Test
        void T16_판정_뒤에_늦게_입력한_기준_안_방문은_판정을_지운다() {
            openWith(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 16));
            visit(10, LocalDate.of(2026, 11, 14));
            assertThat(schedules.findById(1).orElseThrow().missedAt()).isNull();
        }

        @Test
        void 판정_뒤_기준_밖_방문은_판정을_남긴다() {
            openWith(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 16));
            visit(10, LocalDate.of(2026, 11, 24));
            assertThat(schedules.findById(1).orElseThrow().missedAt()).isEqualTo(LocalDate.of(2026, 11, 16));
        }

        @Test
        void 미용_방문은_개인_주기로_다음_미용을_연다() {
            for (int i = 0; i < 3; i++) {
                source.visits.add(new VisitInfo(20 + i, 1, ServiceType.GROOMING, null, LocalDate.of(2026, 9, 1).plusDays(42L * i)));
            }
            service.onVisitRecorded(22);
            // 미용 9/1 · 10/13 · 11/24 → 간격 42 · 42의 중앙값 42, 마지막 미용일 + 42
            assertThat(open(1, ServiceType.GROOMING, null).dueDate()).isEqualTo(LocalDate.of(2026, 11, 24).plusDays(42));
            assertThat(schedules.groomIntervals.get(1L).basis()).isEqualTo(GroomIntervalBasis.MEDIAN);
        }

        @Test
        void 같은_방문으로_다시_불려도_아무것도_바뀌지_않는다() {
            openWith(LocalDate.of(2026, 11, 1), null);
            CycleService.VisitOutcome first = visit(10, LocalDate.of(2026, 11, 10));
            int rows = schedules.rows.size();
            CycleService.VisitOutcome again = service.onVisitRecorded(10);
            assertThat(schedules.rows).hasSize(rows);
            assertThat(again).isEqualTo(first);
        }

        @Test
        void 같은_날_같은_종류를_두_번_입력하면_다음_주기를_닫지_않는다() {
            openWith(LocalDate.of(2026, 11, 1), null);
            visit(10, LocalDate.of(2026, 11, 10));
            long next = open(1, ServiceType.VACCINE, RABIES).scheduleId();
            CycleService.VisitOutcome dup = visit(11, LocalDate.of(2026, 11, 10));
            assertThat(dup.completedScheduleId()).isNull();
            assertThat(schedules.findById(next).orElseThrow().status()).isEqualTo(Schedule.Status.OPEN);
        }

        @Test
        void 완료된_주기에_속하는_늦은_기준_안_방문은_그_주기의_판정만_지운다() {
            // 11-01 예정을 11-20(기준 밖)으로 완료 → 판정 11-16, 다음 주기 열림
            openWith(LocalDate.of(2026, 11, 1), null);
            visit(10, LocalDate.of(2026, 11, 20));
            long next = open(1, ServiceType.VACCINE, RABIES).scheduleId();
            // 나중에 11-10(기준 안) 방문을 입력
            CycleService.VisitOutcome late = visit(11, LocalDate.of(2026, 11, 10));

            assertThat(schedules.findById(1).orElseThrow().missedAt()).isNull();
            assertThat(late.completedScheduleId()).isNull();
            assertThat(schedules.findById(next).orElseThrow().status()).isEqualTo(Schedule.Status.OPEN);
        }

        @Test
        void 없는_방문이면_멈춘다() {
            assertThatThrownBy(() -> service.onVisitRecorded(999)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("다시 계산 (POL-CYC-005, FR-CYC-003)")
    class Recalc {

        @BeforeEach
        void pet() {
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2026, 10, 1), true, 70, TODAY));
        }

        @Test
        void 아직_오지_않은_예정일만_바뀌고_지난_예정일은_그대로() {
            schedules.put(new Schedule(1, 1, ServiceType.VACCINE, RABIES, LocalDate.of(2027, 1, 1), Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));
            schedules.put(new Schedule(2, 1, ServiceType.GROOMING, null, LocalDate.of(2026, 11, 1), Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));

            service.recalculatePet(1);

            assertThat(schedules.findById(1).orElseThrow().dueDate()).isEqualTo(LocalDate.of(2026, 12, 30));
            assertThat(schedules.findById(2).orElseThrow().dueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        }

        @Test
        void 규칙이_없어진_종류의_아직_오지_않은_주기는_VOID() {
            source.rules.clear();
            schedules.put(new Schedule(1, 1, ServiceType.VACCINE, RABIES, LocalDate.of(2027, 1, 1), Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));
            service.recalculatePet(1);
            assertThat(schedules.findById(1).orElseThrow().status()).isEqualTo(Schedule.Status.VOID);
        }

        @Test
        void 무지개다리가_되면_아직_오지_않은_주기는_VOID_지난_주기는_그대로() {
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2026, 10, 1), false, 70, TODAY));
            schedules.put(new Schedule(1, 1, ServiceType.VACCINE, RABIES, LocalDate.of(2027, 1, 1), Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));
            schedules.put(new Schedule(2, 1, ServiceType.GROOMING, null, LocalDate.of(2026, 11, 1), Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));
            service.recalculatePet(1);
            assertThat(schedules.findById(1).orElseThrow().status()).isEqualTo(Schedule.Status.VOID);
            assertThat(schedules.findById(2).orElseThrow().status()).isEqualTo(Schedule.Status.OPEN);
        }

        @Test
        void 규칙_저장_뒤에는_쓰는_아이_모두_다시_계산한다() {
            source.pets.put(2L, new PetProfile(2, LocalDate.of(2026, 10, 1), true, 70, TODAY));
            source.pets.put(3L, new PetProfile(3, LocalDate.of(2026, 10, 1), false, 70, TODAY));
            service.recalculateByRule();
            assertThat(schedules.findOpenByPet(1)).hasSize(2);
            assertThat(schedules.findOpenByPet(2)).hasSize(2);
            assertThat(schedules.findOpenByPet(3)).isEmpty();
        }

        @Test
        void 프로필_바뀜_이벤트를_들으면_다시_계산한다() {
            schedules.put(new Schedule(1, 1, ServiceType.VACCINE, RABIES, LocalDate.of(2027, 1, 1), Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));
            new CycleEventListener(service).onPetProfileChanged(new com.dadayiksun.crm.customer.domain.PetProfileChangedEvent(1L));
            assertThat(schedules.findById(1).orElseThrow().dueDate()).isEqualTo(LocalDate.of(2026, 12, 30));
        }

        @Test
        void 설정을_바꿔도_이미_열린_주기의_기준은_그대로() {
            schedules.put(new Schedule(1, 1, ServiceType.VACCINE, RABIES, LocalDate.of(2027, 1, 1), Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));
            new CycleService(schedules, source, CycleFakes.settings(21), CycleFakes.clockAt(TODAY)).recalculatePet(1);
            assertThat(schedules.findById(1).orElseThrow().missThresholdDays()).isEqualTo(14);
            assertThat(open(1, ServiceType.GROOMING, null).missThresholdDays()).isEqualTo(21); // 새로 연 주기만 새 기준
        }
    }

    @Nested
    @DisplayName("누락 판정 작업 (K20)")
    class Job {

        @Test
        void 예정일_14일_뒤_당일은_누락이_아니고_다음_날_원래_판정일로_누락() {
            schedules.put(new Schedule(1, 1, ServiceType.VACCINE, RABIES, LocalDate.of(2026, 11, 1), Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));
            MissJudgementJob onDay15 = new MissJudgementJob(schedules, CycleFakes.clockAt(LocalDate.of(2026, 11, 15)));
            assertThat(onDay15.run()).isZero();

            // 하루 건너뛰고 17일에 돌아도 원래 판정일 16일
            MissJudgementJob late = new MissJudgementJob(schedules, CycleFakes.clockAt(LocalDate.of(2026, 11, 17)));
            assertThat(late.run()).isEqualTo(1);
            assertThat(schedules.findById(1).orElseThrow().missedAt()).isEqualTo(LocalDate.of(2026, 11, 16));
            assertThat(late.run()).isZero();
        }
    }

    @Nested
    @DisplayName("C12 연락 상태 인터페이스")
    class Contact {

        @Test
        void 정해진_값만_받는다() {
            schedules.put(new Schedule(1, 1, ServiceType.VACCINE, RABIES, TODAY, Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));
            ScheduleContactUpdater updater = new ScheduleContactUpdater(schedules);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                updater.update(1, "NO_ANSWER", 1, TODAY.plusDays(1));
                assertThat(schedules.findById(1).orElseThrow().contactState()).isEqualTo("NO_ANSWER");
                assertThatThrownBy(() -> updater.update(1, "SOMETHING", 0, null)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> updater.update(1, "BOOKED", -1, null)).isInstanceOf(IllegalArgumentException.class);
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }
    }
}
