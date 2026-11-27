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
        schedules.activePet = petId -> source.petProfile(petId).map(PetProfile::active).orElse(false);
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
        void 열린_주기가_없을_때_들어온_방문을_다시_처리해도_새_주기를_완료하지_않는다() {
            // PR #202 리뷰: 열린 미용 주기가 없을 때 방문 → 새 OPEN만 연다. 같은 방문을 다시 처리하면 그 OPEN을 닫으면 안 된다
            source.visits.add(new VisitInfo(22, 1, ServiceType.GROOMING, null, LocalDate.of(2026, 11, 24)));
            CycleService.VisitOutcome first = service.onVisitRecorded(22);
            Schedule opened = open(1, ServiceType.GROOMING, null);
            assertThat(first.completedScheduleId()).isNull();
            assertThat(opened.openedByVisitId()).isEqualTo(22L);
            int rows = schedules.rows.size();

            CycleService.VisitOutcome again = service.onVisitRecorded(22);

            assertThat(again).isEqualTo(first);
            assertThat(schedules.rows).hasSize(rows);
            assertThat(schedules.findById(opened.scheduleId()).orElseThrow()).isEqualTo(opened);
        }

        @Test
        void 열린_주기가_없을_때_연_주기는_같은_날_다른_방문으로_닫히지_않는다() {
            source.visits.add(new VisitInfo(22, 1, ServiceType.GROOMING, null, LocalDate.of(2026, 11, 24)));
            service.onVisitRecorded(22);
            long opened = open(1, ServiceType.GROOMING, null).scheduleId();

            source.visits.add(new VisitInfo(23, 1, ServiceType.GROOMING, null, LocalDate.of(2026, 11, 24)));
            CycleService.VisitOutcome dup = service.onVisitRecorded(23);

            assertThat(dup.completedScheduleId()).isNull();
            assertThat(schedules.findById(opened).orElseThrow().status()).isEqualTo(Schedule.Status.OPEN);
            assertThat(dup.nextScheduleId()).isEqualTo(opened);
        }

        @Test
        void 열린_주기가_없을_때_연_주기도_뒤_날짜_방문은_완료한다() {
            source.visits.add(new VisitInfo(22, 1, ServiceType.GROOMING, null, LocalDate.of(2026, 11, 1)));
            service.onVisitRecorded(22);
            long opened = open(1, ServiceType.GROOMING, null).scheduleId();

            source.visits.add(new VisitInfo(23, 1, ServiceType.GROOMING, null, LocalDate.of(2026, 11, 24)));
            CycleService.VisitOutcome next = service.onVisitRecorded(23);

            assertThat(next.completedScheduleId()).isEqualTo(opened);
            assertThat(schedules.findById(opened).orElseThrow().doneVisitId()).isEqualTo(23L);
        }

        @Test
        void 방문으로_연_주기에만_그_방문을_남긴다() {
            openWith(LocalDate.of(2026, 11, 1), null);
            visit(10, LocalDate.of(2026, 11, 10));
            assertThat(open(1, ServiceType.VACCINE, RABIES).openedByVisitId()).isEqualTo(10L);
            // 방문과 다른 서비스(미용)로 같이 연 주기는 그 방문이 연 것이 아니다
            assertThat(open(1, ServiceType.GROOMING, null).openedByVisitId()).isNull();
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
        void 늦게_입력한_접종으로_횟수가_바뀌면_다음_예정일을_다시_계산한다() {
            // PR #202 CodeRabbit 예시: 1회 행 A, 2회 행 B(30일), 반복 행 C(365일)
            long type = 50L;
            source.rules.put(type, List.of(
                    new VaccineRule(PRIMARY, 0, 10, 30, 1),
                    new VaccineRule(PRIMARY, 10, 60, 30, 2),
                    new VaccineRule(BOOSTER, 60, null, 365, null)));
            LocalDate birth = TODAY.minusDays(40);
            source.pets.put(1L, new PetProfile(1, birth, true, 70, birth));
            source.visits.add(new VisitInfo(31, 1, ServiceType.VACCINE, type, birth));
            source.visits.add(new VisitInfo(32, 1, ServiceType.VACCINE, type, birth.plusDays(30)));
            schedules.put(new Schedule(1, 1, ServiceType.VACCINE, type, birth, Schedule.Status.DONE, 14, 31L, birth, 0, null, "NONE", 0, null));
            schedules.put(new Schedule(2, 1, ServiceType.VACCINE, type, birth.plusDays(30), Schedule.Status.DONE, 14, 32L,
                    birth.plusDays(30), 0, null, "NONE", 0, null));
            schedules.put(new Schedule(3, 1, ServiceType.VACCINE, type, birth.plusDays(60), Schedule.Status.OPEN, 14,
                    null, null, null, null, "NONE", 0, null));

            source.visits.add(new VisitInfo(33, 1, ServiceType.VACCINE, type, birth.plusDays(15)));
            service.onVisitRecorded(33);

            assertThat(schedules.findById(3).orElseThrow().dueDate()).isEqualTo(birth.plusDays(395));
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
        void 다시_계산한_예정일이_지난_날짜면_오늘로_올리고_누락이_생기지_않는다() {
            // (2) 프로필 바뀜: 미래 주기의 새 예정일이 과거로 나옴 (기록 없음, 등록일도 과거)
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2020, 1, 1), true, 70, LocalDate.of(2026, 1, 1)));
            schedules.put(new Schedule(1, 1, ServiceType.VACCINE, RABIES, LocalDate.of(2027, 1, 1), Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));
            // (1) 규칙 활성화: 열린 주기가 없는 새 종류의 예정일이 과거로 나옴 (오래전 기록)
            long newType = 60L;
            source.rules.put(newType, RABIES_RULES);
            source.visits.add(new VisitInfo(40, 1, ServiceType.VACCINE, newType, LocalDate.of(2021, 1, 1)));

            service.recalculatePet(1);

            assertThat(schedules.findById(1).orElseThrow().dueDate()).isEqualTo(TODAY);
            assertThat(open(1, ServiceType.VACCINE, newType).dueDate()).isEqualTo(TODAY);
            new MissJudgementJob(schedules, CycleFakes.clockAt(TODAY.plusDays(1))).run();
            assertThat(schedules.rows).allMatch(s -> s.missedAt() == null);
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
        void 앱을_켤_때_마이그레이션으로_바뀐_규칙을_이미_있는_아이에게_반영한다() {
            // V4 전: 광견병 첫 예정 생후 90일로 열린 주기 (2026-10-01 + 90 = 2026-12-30), 종합백신 규칙 없음
            schedules.put(new Schedule(1, 1, ServiceType.VACCINE, RABIES, LocalDate.of(2026, 12, 30), Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));
            // V4 뒤: 광견병 112일, 종합백신 2행
            long dhppl = 1L;
            source.rules.put(RABIES, List.of(new VaccineRule(PRIMARY, 112, 365, 365, 1), new VaccineRule(BOOSTER, 365, null, 365, null)));
            source.rules.put(dhppl, List.of(new VaccineRule(PRIMARY, 42, 365, 14, 5), new VaccineRule(BOOSTER, 365, null, 365, null)));

            new RuleRecalculationOnStartup(service).run(null);

            assertThat(schedules.findById(1).orElseThrow().dueDate()).isEqualTo(LocalDate.of(2027, 1, 21));
            // 기록 없는 새 종류: 첫 예정(생후 42일 = 11-12)이 등록일(오늘)보다 이르면 등록일
            assertThat(open(1, ServiceType.VACCINE, dhppl).dueDate()).isEqualTo(TODAY);

            int rows = schedules.rows.size();
            new RuleRecalculationOnStartup(service).run(null); // 다시 켜도 바뀌는 것이 없다
            assertThat(schedules.rows).hasSize(rows);
            assertThat(schedules.findById(1).orElseThrow().dueDate()).isEqualTo(LocalDate.of(2027, 1, 21));
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
    @DisplayName("선택 접종은 기록이 있는 아이만 예정 (#204, 접종 규칙 표 2장 8번)")
    class Selective {

        static final long CORONA = 2L;
        static final long HEARTWORM = 6L;

        @BeforeEach
        void rules() {
            // 생후 2026-10-01, 오늘(11-25) 등록
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2026, 10, 1), true, 70, TODAY));
            source.rules.put(CORONA, List.of(new VaccineRule(PRIMARY, 42, 365, 14, 2), new VaccineRule(BOOSTER, 365, null, 365, null)));
            source.rules.put(HEARTWORM, List.of(new VaccineRule(com.dadayiksun.crm.cycle.domain.VaccinePhase.PREVENTIVE, 56, null, 30, null)));
            source.selective.add(CORONA); // 주요 접종 아님 · PREVENTIVE 없음
        }

        private void coronaVisit(long id, LocalDate date) {
            source.visits.add(new VisitInfo(id, 1, ServiceType.VACCINE, CORONA, date));
        }

        @Test
        void 기록_없는_아이를_등록하면_선택_접종_예정은_없고_주요_접종_예방은_있다() {
            service.createInitialSchedules(1);
            assertThat(schedules.findOpen(1, ServiceType.VACCINE, CORONA)).isEmpty();
            assertThat(schedules.findOpen(1, ServiceType.VACCINE, RABIES)).isPresent();
            assertThat(schedules.findOpen(1, ServiceType.VACCINE, HEARTWORM)).isPresent();
            assertThat(schedules.findOpen(1, ServiceType.GROOMING, null)).isPresent();
        }

        @Test
        void 선택_접종을_한_번_맞으면_그_종류의_다음_예정이_생긴다_K24_횟수_우선() {
            service.createInitialSchedules(1);
            coronaVisit(50, LocalDate.of(2026, 11, 20)); // 생후 50일, 기초 2회 중 1회
            service.onVisitRecorded(50);
            assertThat(open(1, ServiceType.VACCINE, CORONA).dueDate()).isEqualTo(LocalDate.of(2026, 12, 4)); // + 14일
        }

        @Test
        void 다시_계산해도_기록_있는_아이는_예정을_두고_기록_없는_아이는_만들지_않는다() {
            source.pets.put(2L, new PetProfile(2, LocalDate.of(2026, 10, 1), true, 70, TODAY));
            coronaVisit(50, LocalDate.of(2026, 11, 20));
            service.recalculateByRule();
            assertThat(open(1, ServiceType.VACCINE, CORONA).dueDate()).isEqualTo(LocalDate.of(2026, 12, 4));
            assertThat(schedules.findOpen(2, ServiceType.VACCINE, CORONA)).isEmpty();
            assertThat(schedules.findOpen(2, ServiceType.VACCINE, RABIES)).isPresent();
        }

        @Test
        void 기록이_모두_사라지면_아직_오지_않은_선택_접종_예정은_VOID() {
            schedules.put(new Schedule(1, 1, ServiceType.VACCINE, CORONA, LocalDate.of(2026, 12, 4), Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));
            service.recalculatePet(1);
            assertThat(schedules.findById(1).orElseThrow().status()).isEqualTo(Schedule.Status.VOID);
        }
    }

    @Nested
    @DisplayName("누락 판정 작업 (K20)")
    class Job {

        @Test
        void 예정일_14일_뒤_당일은_누락이_아니고_다음_날_원래_판정일로_누락() {
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2025, 1, 1), true, 70, LocalDate.of(2025, 6, 1)));
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

        @Test
        void 무지개다리_아이의_지난_OPEN은_판정하지_않는다() {
            source.pets.put(1L, new PetProfile(1, LocalDate.of(2025, 1, 1), false, 70, LocalDate.of(2025, 6, 1)));
            schedules.put(new Schedule(1, 1, ServiceType.VACCINE, RABIES, LocalDate.of(2026, 11, 1), Schedule.Status.OPEN,
                    14, null, null, null, null, "NONE", 0, null));
            MissJudgementJob job = new MissJudgementJob(schedules, CycleFakes.clockAt(LocalDate.of(2026, 11, 20)));
            assertThat(job.run()).isZero();
            assertThat(schedules.findById(1).orElseThrow().missedAt()).isNull();
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
