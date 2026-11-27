package com.dadayiksun.crm.cycle.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.dadayiksun.crm.common.setting.SettingService;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.cycle.domain.GroomInterval;
import com.dadayiksun.crm.cycle.domain.PetProfile;
import com.dadayiksun.crm.cycle.domain.Schedule;
import com.dadayiksun.crm.cycle.domain.ServiceType;
import com.dadayiksun.crm.cycle.domain.VaccineRule;
import com.dadayiksun.crm.cycle.domain.VisitInfo;
import com.dadayiksun.crm.cycle.repository.CycleSourceQuery;
import com.dadayiksun.crm.cycle.repository.ScheduleRepository;

/** DB 없이 C4 흐름을 확인하는 메모리 저장소 (SQL은 로컬 MySQL로 따로 확인). */
final class CycleFakes {

    private CycleFakes() {
    }

    static KstClock clockAt(LocalDate today) {
        return new KstClock(Clock.fixed(ZonedDateTime.of(today.atTime(10, 0), KstClock.ZONE).toInstant(), KstClock.ZONE));
    }

    /** setting 값을 메모리에서 준다 */
    static SettingService settings(int missThresholdDays) {
        return new SettingService(null) {
            @Override
            public int getInt(String key) {
                return missThresholdDays;
            }
        };
    }

    static final class Schedules implements ScheduleRepository {
        final List<Schedule> rows = new ArrayList<>();
        final Map<Long, GroomInterval> groomIntervals = new HashMap<>();
        private long seq = 100;

        @Override
        public Optional<Schedule> findById(long scheduleId) {
            return rows.stream().filter(s -> s.scheduleId() == scheduleId).findFirst();
        }

        @Override
        public Optional<Schedule> findOpen(long petId, ServiceType serviceType, Long vaccineTypeId) {
            return rows.stream().filter(s -> s.status() == Schedule.Status.OPEN && s.petId() == petId
                    && s.serviceType() == serviceType && Objects.equals(s.vaccineTypeId(), vaccineTypeId)).findFirst();
        }

        @Override
        public List<Schedule> findOpenByPet(long petId) {
            return rows.stream().filter(s -> s.status() == Schedule.Status.OPEN && s.petId() == petId).toList();
        }

        @Override
        public List<Schedule> findByPet(long petId) {
            return rows.stream().filter(s -> s.petId() == petId).sorted(Comparator.comparing(Schedule::dueDate)).toList();
        }

        @Override
        public long insertOpen(long petId, ServiceType serviceType, Long vaccineTypeId, LocalDate dueDate, int threshold) {
            if (findOpen(petId, serviceType, vaccineTypeId).isPresent()) {
                throw new IllegalStateException("active_key 중복");
            }
            long id = ++seq;
            rows.add(new Schedule(id, petId, serviceType, vaccineTypeId, dueDate, Schedule.Status.OPEN, threshold,
                    null, null, null, null, "NONE", 0, null));
            return id;
        }

        @Override
        public void complete(long scheduleId, long visitId, LocalDate doneDate, int gapDays, LocalDate missedAt) {
            replace(scheduleId, s -> new Schedule(s.scheduleId(), s.petId(), s.serviceType(), s.vaccineTypeId(), s.dueDate(),
                    Schedule.Status.DONE, s.missThresholdDays(), visitId, doneDate, gapDays, missedAt,
                    s.contactState(), s.noAnswerCount(), s.nextContactDate()));
        }

        @Override
        public void clearMissedAt(long scheduleId) {
            replace(scheduleId, s -> new Schedule(s.scheduleId(), s.petId(), s.serviceType(), s.vaccineTypeId(), s.dueDate(),
                    s.status(), s.missThresholdDays(), s.doneVisitId(), s.doneDate(), s.gapDays(), null,
                    s.contactState(), s.noAnswerCount(), s.nextContactDate()));
        }

        @Override
        public void updateDueDate(long scheduleId, LocalDate dueDate) {
            replace(scheduleId, s -> new Schedule(s.scheduleId(), s.petId(), s.serviceType(), s.vaccineTypeId(), dueDate,
                    s.status(), s.missThresholdDays(), s.doneVisitId(), s.doneDate(), s.gapDays(), s.missedAt(),
                    s.contactState(), s.noAnswerCount(), s.nextContactDate()));
        }

        @Override
        public void voidSchedule(long scheduleId) {
            replace(scheduleId, s -> new Schedule(s.scheduleId(), s.petId(), s.serviceType(), s.vaccineTypeId(), s.dueDate(),
                    Schedule.Status.VOID, s.missThresholdDays(), s.doneVisitId(), s.doneDate(), s.gapDays(), s.missedAt(),
                    s.contactState(), s.noAnswerCount(), s.nextContactDate()));
        }

        @Override
        public int markMissed(LocalDate today) {
            int n = 0;
            for (Schedule s : List.copyOf(rows)) {
                if (s.status() == Schedule.Status.OPEN && s.missedAt() == null
                        && s.dueDate().plusDays(s.missThresholdDays()).isBefore(today)) {
                    LocalDate judged = s.dueDate().plusDays(s.missThresholdDays() + 1L);
                    replace(s.scheduleId(), x -> new Schedule(x.scheduleId(), x.petId(), x.serviceType(), x.vaccineTypeId(),
                            x.dueDate(), x.status(), x.missThresholdDays(), x.doneVisitId(), x.doneDate(), x.gapDays(),
                            judged, x.contactState(), x.noAnswerCount(), x.nextContactDate()));
                    n++;
                }
            }
            return n;
        }

        @Override
        public void updateContact(long scheduleId, String contactState, int noAnswerCount, LocalDate nextContactDate) {
            replace(scheduleId, s -> new Schedule(s.scheduleId(), s.petId(), s.serviceType(), s.vaccineTypeId(), s.dueDate(),
                    s.status(), s.missThresholdDays(), s.doneVisitId(), s.doneDate(), s.gapDays(), s.missedAt(),
                    contactState, noAnswerCount, nextContactDate));
        }

        @Override
        public void saveGroomInterval(long petId, GroomInterval interval) {
            groomIntervals.put(petId, interval);
        }

        /** 테스트에서 지난 주기를 직접 만든다 */
        Schedule put(Schedule s) {
            rows.add(s);
            return s;
        }

        private void replace(long id, java.util.function.UnaryOperator<Schedule> f) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).scheduleId() == id) {
                    rows.set(i, f.apply(rows.get(i)));
                }
            }
        }
    }

    static final class Source implements CycleSourceQuery {
        final Map<Long, PetProfile> pets = new HashMap<>();
        final List<VisitInfo> visits = new ArrayList<>();
        final Map<Long, List<VaccineRule>> rules = new LinkedHashMap<>();

        @Override
        public Optional<PetProfile> petProfile(long petId) {
            return Optional.ofNullable(pets.get(petId));
        }

        @Override
        public List<LocalDate> visitDates(long petId, ServiceType serviceType, Long vaccineTypeId) {
            return visits.stream()
                    .filter(v -> v.petId() == petId && v.serviceType() == serviceType
                            && Objects.equals(v.vaccineTypeId(), vaccineTypeId))
                    .map(VisitInfo::visitDate).sorted().toList();
        }

        @Override
        public Optional<VisitInfo> visit(long visitId) {
            return visits.stream().filter(v -> v.visitId() == visitId).findFirst();
        }

        @Override
        public List<Long> activePetIds() {
            return pets.values().stream().filter(PetProfile::active).map(PetProfile::petId).sorted().toList();
        }

        @Override
        public Map<Long, List<VaccineRule>> activeRulesByType() {
            return rules;
        }
    }
}
