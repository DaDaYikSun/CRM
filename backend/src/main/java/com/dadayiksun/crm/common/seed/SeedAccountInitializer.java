package com.dadayiksun.crm.common.seed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 관리자 1 + 시연용 데스크 · 미용사 계정을 넣는다 (FR-DATA-003, POL-ACC-009, DB 설계 8장).
 * 비밀번호는 환경 변수에서 읽어 BCrypt 해시로 저장한다 (NFR-SEC-001). 비밀번호가 비면 그 계정은 만들지 않는다.
 * 같은 로그인 아이디가 이미 있으면 건너뛴다 (서버를 다시 켜도 두 번 들어가지 않음).
 * staff 테이블의 쓰기 주인은 auth · admin이지만, 첫 계정은 로그인 전이라 공통 기반이 넣는다 (#142 담당 파일).
 */
@Component
public class SeedAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedAccountInitializer.class);

    /** [제안] 시드 계정 아이디 · 이름. 문서에 정해진 값이 없어 #142 PR에서 제안한다. */
    record SeedAccount(String loginId, String name, String role, String password) {
    }

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties properties;

    public SeedAccountInitializer(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, SeedProperties properties) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SeedAccount> accounts = List.of(
                new SeedAccount("admin", "관리자", "ADMIN", properties.adminPassword()),
                new SeedAccount("desk", "데스크", "DESK", properties.deskPassword()),
                new SeedAccount("groomer", "미용사", "GROOMER", properties.groomerPassword()));
        for (SeedAccount account : accounts) {
            insertIfAbsent(account);
        }
    }

    private void insertIfAbsent(SeedAccount account) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM staff WHERE login_id = ?", Integer.class, account.loginId());
        if (count != null && count > 0) {
            return;
        }
        if (account.password() == null || account.password().isBlank()) {
            log.warn("시드 계정 '{}'의 비밀번호 환경 변수가 비어 계정을 만들지 않았습니다 (.env.example 참고)", account.loginId());
            return;
        }
        jdbc.update("INSERT INTO staff (login_id, password_hash, name, role) VALUES (?, ?, ?, ?)",
                account.loginId(), passwordEncoder.encode(account.password()), account.name(), account.role());
        log.info("시드 계정 '{}'을(를) 만들었습니다", account.loginId());
    }
}
