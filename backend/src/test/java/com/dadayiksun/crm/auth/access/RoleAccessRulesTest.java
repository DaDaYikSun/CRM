package com.dadayiksun.crm.auth.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.dadayiksun.crm.auth.domain.Role;

/** API 설계 2장 허용 표 그대로인지 확인한다. 아직 없는 API도 경로 규칙으로 본다 (이슈 #143 완료 조건). */
class RoleAccessRulesTest {

    private static final Map<String, String[]> NONE = Map.of();

    private static boolean allowed(Role role, String method, String path) {
        return RoleAccessRules.allows(role, method, path, NONE);
    }

    /** 미용사 거부 목록 전체 (POL-ACC-011, T3 · T10) */
    @ParameterizedTest
    @CsvSource({
            "GET,/api/targets", "GET,/api/targets/summary",
            "POST,/api/contacts", "POST,/api/contacts/sms", "POST,/api/contacts/sms/preview", "PATCH,/api/contacts/7",
            "GET,/api/guardians/1/contact-panel", "GET,/api/guardians/1/contacts",
            "GET,/api/guardians/1", "PATCH,/api/guardians/1", "POST,/api/guardians/1/phone-confirm",
            "GET,/api/guardians/phone-check", "POST,/api/guardians", "POST,/api/guardians/1/pets",
            "PATCH,/api/pets/1", "POST,/api/pets/1/photo", "POST,/api/pets/1/rainbow", "POST,/api/pets/1/rainbow-revert",
            "GET,/api/dashboard/summary", "GET,/api/reports/1",
            "GET,/api/admin/staff", "POST,/api/admin/staff", "PUT,/api/admin/rules/1"
    })
    void 미용사는_거부(String method, String path) {
        assertThat(allowed(Role.GROOMER, method, path)).as("%s %s", method, path).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/options/breeds", "GET,/api/search/preview", "GET,/api/search", "GET,/api/pets/1",
            "GET,/api/pets/1/timeline", "POST,/api/pets/1/visits", "GET,/api/grooming", "PATCH,/api/visits/3",
            "GET,/api/edit-history", "GET,/api/auth/me", "POST,/api/auth/logout", "POST,/api/auth/password"
    })
    void 미용사도_허용(String method, String path) {
        assertThat(allowed(Role.GROOMER, method, path)).as("%s %s", method, path).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/targets", "POST,/api/contacts", "GET,/api/guardians/1", "POST,/api/guardians",
            "PATCH,/api/pets/1", "POST,/api/pets/1/rainbow", "GET,/api/dashboard/summary", "GET,/api/reports/1",
            "GET,/api/pets/1", "POST,/api/pets/1/visits", "GET,/api/grooming"
    })
    void 데스크는_허용(String method, String path) {
        assertThat(allowed(Role.DESK, method, path)).as("%s %s", method, path).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/pets/1/rainbow-revert", "GET,/api/admin/staff", "PUT,/api/admin/settings",
            "GET,/api/admin", "POST,/api/admin/staff/1/reset-password"})
    void 데스크는_관리자_전용을_거부(String method, String path) {
        assertThat(allowed(Role.DESK, method, path)).as("%s %s", method, path).isFalse();
        assertThat(allowed(Role.ADMIN, method, path)).as("%s %s", method, path).isTrue();
    }

    @Test
    void 관리자는_표의_모든_API를_허용() {
        assertThat(allowed(Role.ADMIN, "GET", "/api/targets")).isTrue();
        assertThat(allowed(Role.ADMIN, "GET", "/api/guardians/1")).isTrue();
        assertThat(allowed(Role.ADMIN, "GET", "/api/anything-new")).isTrue();
    }

    @Test
    void 타임라인_접종_연락_필터와_연락_수정이력은_미용사_거부() {
        assertThat(RoleAccessRules.allows(Role.GROOMER, "GET", "/api/pets/1/timeline", Map.of("type", new String[] {"VACCINE"}))).isFalse();
        assertThat(RoleAccessRules.allows(Role.GROOMER, "GET", "/api/pets/1/timeline", Map.of("type", new String[] {"contact"}))).isFalse();
        assertThat(RoleAccessRules.allows(Role.GROOMER, "GET", "/api/pets/1/timeline", Map.of("type", new String[] {"ALL", "VACCINE"}))).isFalse();
        assertThat(RoleAccessRules.allows(Role.GROOMER, "GET", "/api/pets/1/timeline", Map.of("type", new String[] {"GROOMING"}))).isTrue();
        assertThat(RoleAccessRules.allows(Role.GROOMER, "GET", "/api/pets/1/timeline", Map.of("type", new String[] {"ALL"}))).isTrue();
        assertThat(RoleAccessRules.allows(Role.DESK, "GET", "/api/pets/1/timeline", Map.of("type", new String[] {"VACCINE"}))).isTrue();
        assertThat(RoleAccessRules.allows(Role.GROOMER, "GET", "/api/edit-history", Map.of("targetType", new String[] {"CONTACT_LOG"}))).isFalse();
        assertThat(RoleAccessRules.allows(Role.GROOMER, "GET", "/api/edit-history", Map.of("targetType", new String[] {"VISIT"}))).isTrue();
    }

    /** 규칙이 열어 두는 경우. 이때 미용사에게 접종 · 연락 줄을 빼는 일은 API가 한다 (#149, 초안 28). */
    @Test
    void type_없는_타임라인과_targetType_없는_수정이력은_규칙을_통과한다() {
        assertThat(RoleAccessRules.allows(Role.GROOMER, "GET", "/api/pets/1/timeline", Map.of())).isTrue();
        assertThat(RoleAccessRules.allows(Role.GROOMER, "GET", "/api/edit-history", Map.of())).isTrue();
    }

    @Test
    void 표에_없는_API는_미용사_기본_거부이고_HEAD는_GET과_같다() {
        assertThat(allowed(Role.GROOMER, "GET", "/api/never-heard-of")).isFalse();
        assertThat(allowed(Role.DESK, "GET", "/api/never-heard-of")).isTrue();
        assertThat(allowed(Role.GROOMER, "HEAD", "/api/guardians/1")).isFalse();
        assertThat(allowed(Role.GROOMER, "HEAD", "/api/pets/1")).isTrue();
    }

    @Test
    void 로그인_전에_열린_주소는_둘뿐() {
        assertThat(RoleAccessRules.isPublic("POST", "/api/auth/login")).isTrue();
        assertThat(RoleAccessRules.isPublic("GET", "/api/csrf")).isTrue();
        assertThat(RoleAccessRules.isPublic("POST", "/api/auth/logout")).isTrue();
        assertThat(RoleAccessRules.isPublic("GET", "/api/auth/logout")).isFalse();
        assertThat(RoleAccessRules.isPublic("GET", "/api/auth/me")).isFalse();
        assertThat(RoleAccessRules.isPublic("GET", "/api/auth/login")).isFalse();
    }
}
