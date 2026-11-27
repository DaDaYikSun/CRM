package com.dadayiksun.crm.data;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

/**
 * 시연용 더미 데이터 적재 (FR-DATA-001, #147, 팀 결정 K8). local · demo 프로필에서만 돈다. 운영에서는 이 빈이 없다.
 *
 * <ul>
 *   <li>CSV(db/dummy)를 직원 → 보호자 → 반려견 → 방문(접종 · 미용) 순서로 넣는다. CSV 번호(guardian_id · pet_id)는
 *       CSV 안 번호라 DB가 준 id로 바꿔 넣는다 (db/dummy/README.md). 빈 DB를 가정하지 않는다.</li>
 *   <li>보호자가 이미 한 명이라도 있으면 넣지 않는다 (앱을 다시 켜도 두 번 들어가지 않음). 전부 한 트랜잭션이라 실패하면 아무것도 남지 않는다.</li>
 *   <li>더미 직원 비밀번호는 환경 변수 {@code DUMMY_STAFF_PASSWORD} 하나를 BCrypt로 저장한다 (NFR-SEC-001). 비면 적재하지 않는다.</li>
 *   <li>예정 일정(schedule) · 누락 판정 · 연락 기록은 아직 넣지 않는다. contact_log.schedule_id가 필수라 #144 2단계(예정 일정 만들기)
 *       뒤에 이 러너에 더한다. is_converted는 #154 규칙으로 채운다.</li>
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

    public DummyDataLoader(JdbcTemplate jdbc, TransactionTemplate tx, PasswordEncoder passwordEncoder,
                           @Value("${crm.dummy.staff-password:}") String staffPassword) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.passwordEncoder = passwordEncoder;
        this.staffPassword = staffPassword;
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
        log.info("더미 데이터를 넣었습니다: 직원 {}, 보호자 {}, 반려견 {}, 접종 {}, 미용 {}",
                r.staff(), r.guardians(), r.pets(), r.vaccinations(), r.groomings());
    }

    record Result(int staff, int guardians, int pets, int vaccinations, int groomings) {
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
        insertVisits(vaccinations, "VACCINE", petIds, vaccineIds, staffIds);
        insertVisits(groomings, "GROOMING", petIds, vaccineIds, staffIds);
        return new Result(staffIds.size(), guardianIds.size(), petIds.size(), vaccinations.size(), groomings.size());
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
                p.get("status"), time(p.get("rainbow_at")), date(p.get("contact_hold_until")),
                require(staffIds, p.get("created_by_login_id"), "직원"), time(p.get("created_at")));
    }

    private void insertVisits(List<Map<String, String>> rows, String serviceType, Map<String, Long> petIds,
                              Map<String, Long> vaccineIds, Map<String, Long> staffIds) {
        jdbc.batchUpdate("""
                INSERT INTO visit (pet_id, service_type, vaccine_type_id, visit_date, memo, brought_by_phone, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, rows, 500, (ps, v) -> {
            ps.setLong(1, require(petIds, v.get("pet_id"), "반려견"));
            ps.setString(2, serviceType);
            if ("VACCINE".equals(serviceType)) {
                ps.setLong(3, require(vaccineIds, v.get("vaccine_type_code"), "접종 종류"));
            } else {
                ps.setNull(3, Types.BIGINT);
            }
            ps.setDate(4, date(v.get("visit_date")));
            ps.setString(5, blank(v.get("memo")));
            ps.setString(6, blank(v.get("brought_by_phone")));
            ps.setLong(7, require(staffIds, v.get("created_by_login_id"), "직원"));
            ps.setTimestamp(8, time(v.get("created_at")));
        });
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
