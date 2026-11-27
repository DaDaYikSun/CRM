package com.dadayiksun.crm.data;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.dadayiksun.crm.customer.domain.Chosung;
import com.dadayiksun.crm.cycle.domain.Schedule;
import com.dadayiksun.crm.cycle.domain.ServiceType;
import com.dadayiksun.crm.cycle.service.CycleQueryService;
import com.dadayiksun.crm.cycle.service.CycleService;
import com.dadayiksun.crm.cycle.service.MissJudgementJob;

/**
 * 시연용 더미 데이터 적재 (FR-DATA-001, #147, 팀 결정 K8). local · demo 프로필에서만 돈다. 운영에서는 이 빈이 없다.
 *
 * <ul>
 *   <li>CSV(db/dummy)를 직원 → 보호자 → 반려견 → 방문(접종 · 미용) 순서로 넣는다. CSV 번호(guardian_id · pet_id)는
 *       CSV 안 번호라 DB가 준 id로 바꿔 넣는다 (db/dummy/README.md). 빈 DB를 가정하지 않는다.</li>
 *   <li>보호자가 이미 한 명이라도 있으면 넣지 않는다 (앱을 다시 켜도 두 번 들어가지 않음). 전부 한 트랜잭션이라 실패하면 아무것도 남지 않는다.</li>
 *   <li>더미 직원 비밀번호는 환경 변수 {@code DUMMY_STAFF_PASSWORD} 하나를 BCrypt로 저장한다 (NFR-SEC-001). 비면 적재하지 않는다.</li>
 *   <li>예정 일정은 적재기가 따로 계산하지 않고 cycle 주인 경로를 아이마다 부른다 (#144, PR #201 리뷰 1번).
 *       등록일 전 방문을 넣고 첫 예정 일정({@code createInitialSchedules}), 그 뒤 방문 · 연락을 시간 순서로
 *       방문은 넣고 '방문 기록됨'({@code onVisitRecorded}), 연락은 그때 열린 예정 일정에 붙인다 ({@link DummyTimeline}).
 *       무지개다리 아이는 기록을 다 넣은 뒤 상태를 바꾸고 다시 계산한다. 끝에 누락 판정 작업을 한 번 돌린다 (K20).</li>
 *   <li>붙일 예정 일정이 없는 연락(규칙 행이 아직 없는 선택 접종 · 등록 전 연락)은 넣지 않고 수만 남긴다.</li>
 *   <li>주기의 연락 상태(contact_state 등)와 전환(is_converted)은 아직 채우지 않는다. #154 판정 함수가 생기면 이 러너에서 부른다.</li>
 * </ul>
 *
 * <p>K17(쓰기는 주인 도메인만)의 예외다: 시연 데이터라 staff(auth) · guardian · pet(customer) · visit(visit)에 바로 쓴다 (#147 남은 질문 4).
 */
@Component
@Profile({"local", "demo"})
@Order(Ordered.LOWEST_PRECEDENCE)
public class DummyDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DummyDataLoader.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PasswordEncoder passwordEncoder;
    private final String staffPassword;
    private final CycleService cycle;
    private final CycleQueryService cycleQuery;
    private final MissJudgementJob missJudgement;

    public DummyDataLoader(JdbcTemplate jdbc, TransactionTemplate tx, PasswordEncoder passwordEncoder,
                           @Value("${crm.dummy.staff-password:}") String staffPassword,
                           CycleService cycle, CycleQueryService cycleQuery, MissJudgementJob missJudgement) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.passwordEncoder = passwordEncoder;
        this.staffPassword = staffPassword;
        this.cycle = cycle;
        this.cycleQuery = cycleQuery;
        this.missJudgement = missJudgement;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (staffPassword == null || staffPassword.isBlank()) {
            log.warn("DUMMY_STAFF_PASSWORD가 비어 더미 데이터를 넣지 않았습니다 (.env.example 참고)");
            return;
        }
        Integer guardians = jdbc.queryForObject("SELECT COUNT(*) FROM guardian", Integer.class);
        if (guardians != null && guardians > 0) {
            log.info("보호자가 이미 {}명 있어 더미 데이터를 넣지 않았습니다", guardians);
            return;
        }
        Result r = tx.execute(status -> load());
        log.info("더미 데이터를 넣었습니다: 직원 {}, 보호자 {}, 반려견 {}, 접종 {}, 미용 {}, 예정 일정 {}, 누락 판정 {}, 연락 {}"
                        + " (붙일 예정 일정이 없어 뺀 연락: 등록 전 {}, 열린 예정 없음 {})",
                r.staff(), r.guardians(), r.pets(), r.vaccinations(), r.groomings(), r.schedules(), r.missed(),
                r.contacts(), r.contactsBeforeRegistration(), r.contactsWithoutSchedule());
    }

    record Result(int staff, int guardians, int pets, int vaccinations, int groomings, int schedules, int missed,
                  int contacts, int contactsBeforeRegistration, int contactsWithoutSchedule) {
    }

    private Result load() {
        Map<String, Long> staffIds = loadStaff();
        Map<String, Long> breedIds = lookup("SELECT name, breed_id FROM breed WHERE species = 'DOG'");
        Map<String, Long> coatIds = lookup("SELECT code, coat_type_id FROM coat_type");
        Map<String, Long> vaccineIds = lookup("SELECT code, vaccine_type_id FROM vaccine_type");

        Map<String, Long> guardianIds = new HashMap<>();
        for (Map<String, String> o : DummyCsv.read("owners.csv")) {
            guardianIds.put(o.get("guardian_id"), insertGuardian(o, staffIds));
        }

        List<Map<String, String>> pets = DummyCsv.read("pets.csv");
        Map<String, Long> petIds = new HashMap<>();
        for (Map<String, String> p : pets) {
            petIds.put(p.get("pet_id"), insertPet(p, guardianIds, breedIds, coatIds, staffIds));
        }
        // 같은 집 보류를 건 아이(contact_hold_by_pet_id)는 모든 아이를 넣은 뒤 DB id로 잇는다
        for (Map<String, String> p : pets) {
            String holdBy = p.get("contact_hold_by_pet_id");
            if (!holdBy.isEmpty()) {
                jdbc.update("UPDATE pet SET contact_hold_by_pet_id = ? WHERE pet_id = ?",
                        require(petIds, holdBy, "반려견"), petIds.get(p.get("pet_id")));
            }
        }

        List<Map<String, String>> vaccinations = DummyCsv.read("vaccinations.csv");
        List<Map<String, String>> groomings = DummyCsv.read("groomings.csv");
        Map<String, List<DummyTimeline.Event>> eventsByPet = new HashMap<>();
        vaccinations.forEach(v -> eventsByPet.computeIfAbsent(v.get("pet_id"), k -> new ArrayList<>())
                .add(DummyTimeline.visit("VACCINE", v)));
        groomings.forEach(g -> eventsByPet.computeIfAbsent(g.get("pet_id"), k -> new ArrayList<>())
                .add(DummyTimeline.visit("GROOMING", g)));
        DummyCsv.read("contacts.csv").forEach(c -> eventsByPet.computeIfAbsent(c.get("pet_id"), k -> new ArrayList<>())
                .add(DummyTimeline.contact(c)));

        int contacts = 0;
        int contactsBefore = 0;
        int contactsWithout = 0;
        List<Long> loadedPetIds = new ArrayList<>();
        for (Map<String, String> p : pets) {
            long petId = petIds.get(p.get("pet_id"));
            loadedPetIds.add(petId);
            LocalDate registeredOn = LocalDate.parse(p.get("created_at").substring(0, 10));
            DummyTimeline.Plan plan = DummyTimeline.plan(registeredOn,
                    eventsByPet.getOrDefault(p.get("pet_id"), List.of()));
            contactsBefore += plan.contactsBeforeRegistration();

            // 등록할 때 같이 입력한 과거 기록 → 첫 예정 일정 (아이 등록됨)
            for (DummyTimeline.Event e : plan.beforeRegistration()) {
                insertVisit(petId, e, vaccineIds, staffIds);
            }
            cycle.createInitialSchedules(petId);

            for (DummyTimeline.Event e : plan.afterRegistration()) {
                if (e.kind() == DummyTimeline.Kind.VISIT) {
                    cycle.onVisitRecorded(insertVisit(petId, e, vaccineIds, staffIds));
                } else if (insertContact(petId, e.row(), guardianIds, vaccineIds, staffIds)) {
                    contacts++;
                } else {
                    contactsWithout++;
                }
            }

            // 무지개다리 아이: 기록을 다 넣은 뒤 상태를 바꾸고 아직 오지 않은 예정 일정을 정리한다 (DB 설계 5-1)
            if ("RAINBOW".equals(p.get("status"))) {
                jdbc.update("UPDATE pet SET status = 'RAINBOW', rainbow_at = ? WHERE pet_id = ?",
                        time(p.get("rainbow_at")), petId);
                cycle.recalculatePet(petId);
            }
        }
        int missed = missJudgement.run();
        int schedules = loadedPetIds.stream().mapToInt(id -> cycleQuery.findSchedules(id).size()).sum();
        return new Result(staffIds.size(), guardianIds.size(), petIds.size(), vaccinations.size(), groomings.size(),
                schedules, missed, contacts, contactsBefore, contactsWithout);
    }

    /** 같은 로그인 아이디가 있으면 그 직원을 쓰고, 없으면 넣는다 */
    private Map<String, Long> loadStaff() {
        Map<String, Long> ids = new HashMap<>();
        String hash = passwordEncoder.encode(staffPassword);
        for (Map<String, String> s : DummyCsv.read("staff.csv")) {
            String loginId = s.get("login_id");
            List<Long> found = jdbc.queryForList("SELECT staff_id FROM staff WHERE login_id = ?", Long.class, loginId);
            if (!found.isEmpty()) {
                ids.put(loginId, found.get(0));
                continue;
            }
            ids.put(loginId, insert(
                    "INSERT INTO staff (login_id, password_hash, name, role, is_active) VALUES (?, ?, ?, ?, ?)",
                    loginId, hash, s.get("name"), s.get("role"), bool(s.get("is_active"))));
        }
        return ids;
    }

    private long insertGuardian(Map<String, String> o, Map<String, Long> staffIds) {
        return insert("""
                INSERT INTO guardian (name, name_chosung, phone, sub_phone, preferred_contact, phone_status, phone_checked_at,
                    privacy_agreed, privacy_agreed_at, sms_agreed, sms_agreed_at,
                    tag_no_contact, tag_sms_only, tag_sensitive, tag_contact_time, contact_time_note, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                o.get("name"), Chosung.of(o.get("name")), o.get("phone"), blank(o.get("sub_phone")),
                blank(o.get("preferred_contact")), o.get("phone_status"), date(o.get("phone_checked_at")),
                bool(o.get("privacy_agreed")), time(o.get("privacy_agreed_at")),
                bool(o.get("sms_agreed")), time(o.get("sms_agreed_at")),
                bool(o.get("tag_no_contact")), bool(o.get("tag_sms_only")), bool(o.get("tag_sensitive")),
                bool(o.get("tag_contact_time")), blank(o.get("contact_time_note")),
                require(staffIds, o.get("created_by_login_id"), "직원"), time(o.get("created_at")));
    }

    private long insertPet(Map<String, String> p, Map<String, Long> guardianIds, Map<String, Long> breedIds,
                           Map<String, Long> coatIds, Map<String, Long> staffIds) {
        return insert("""
                INSERT INTO pet (guardian_id, name, name_chosung, species, breed_id, coat_type_id, birth_date,
                    birth_date_estimated, registration_no, has_pet_insurance, weight_kg, status, rainbow_at,
                    contact_hold_until, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                require(guardianIds, p.get("guardian_id"), "보호자"), p.get("name"), Chosung.of(p.get("name")),
                p.get("species"), require(breedIds, p.get("breed_name"), "품종"),
                require(coatIds, p.get("coat_type_code"), "털 유형"), date(p.get("birth_date")),
                bool(p.get("birth_date_estimated")), blank(p.get("registration_no")),
                p.get("has_pet_insurance").isEmpty() ? null : bool(p.get("has_pet_insurance")),
                p.get("weight_kg").isEmpty() ? null : new BigDecimal(p.get("weight_kg")),
                "ACTIVE", null, date(p.get("contact_hold_until")), // 무지개다리는 기록을 다 넣은 뒤 바꾼다
                require(staffIds, p.get("created_by_login_id"), "직원"), time(p.get("created_at")));
    }

    private long insertVisit(long petId, DummyTimeline.Event e, Map<String, Long> vaccineIds, Map<String, Long> staffIds) {
        Map<String, String> v = e.row();
        Long vaccineTypeId = "VACCINE".equals(e.service())
                ? require(vaccineIds, v.get("vaccine_type_code"), "접종 종류") : null;
        return insert("""
                INSERT INTO visit (pet_id, service_type, vaccine_type_id, visit_date, memo, brought_by_phone, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, petId, e.service(), vaccineTypeId, date(v.get("visit_date")), blank(v.get("memo")),
                blank(v.get("brought_by_phone")), require(staffIds, v.get("created_by_login_id"), "직원"),
                time(v.get("created_at")));
    }

    /** 그때 열린 같은 서비스 · 접종 종류의 예정 일정에 붙인다. 없으면 넣지 않고 false */
    private boolean insertContact(long petId, Map<String, String> c, Map<String, Long> guardianIds,
                                  Map<String, Long> vaccineIds, Map<String, Long> staffIds) {
        ServiceType service = ServiceType.valueOf(c.get("service_type"));
        Long vaccineTypeId = service == ServiceType.VACCINE
                ? require(vaccineIds, c.get("vaccine_type_code"), "접종 종류") : null;
        Optional<Schedule> open = cycleQuery.findOpenSchedules(petId).stream()
                .filter(s -> s.serviceType() == service && Objects.equals(s.vaccineTypeId(), vaccineTypeId))
                .findFirst();
        if (open.isEmpty()) {
            return false;
        }
        insert("""
                INSERT INTO contact_log (contact_group_id, guardian_id, pet_id, schedule_id, method, result, is_mock_sms,
                    sms_template, sms_body, talked_with, memo, next_contact_date, contacted_at, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                c.get("contact_group_id"), require(guardianIds, c.get("guardian_id"), "보호자"), petId,
                open.get().scheduleId(), c.get("method"), blank(c.get("result")), bool(c.get("is_mock_sms")),
                blank(c.get("sms_template")), blank(c.get("sms_body")), blank(c.get("talked_with")), blank(c.get("memo")),
                date(c.get("next_contact_date")), time(c.get("contacted_at")),
                require(staffIds, c.get("created_by_login_id"), "직원"), time(c.get("contacted_at")));
        return true;
    }

    private Map<String, Long> lookup(String sql) {
        Map<String, Long> m = new HashMap<>();
        jdbc.query(sql, rs -> {
            m.put(rs.getString(1), rs.getLong(2));
        });
        return m;
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    /** CSV 참조가 기본 데이터 · 앞서 넣은 행에 없으면 조용히 넘기지 않고 멈춘다 (트랜잭션 전체 되돌림) */
    static long require(Map<String, Long> ids, String key, String what) {
        Long id = ids.get(key);
        if (id == null) {
            throw new IllegalStateException("더미 CSV의 " + what + " '" + key + "'를 찾지 못했다");
        }
        return id;
    }

    private static String blank(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static int bool(String s) {
        return "1".equals(s) ? 1 : 0;
    }

    private static Date date(String s) {
        return s == null || s.isEmpty() ? null : Date.valueOf(s);
    }

    private static Timestamp time(String s) {
        return s == null || s.isEmpty() ? null : Timestamp.valueOf(s);
    }
}
