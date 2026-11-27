package com.dadayiksun.crm.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.dadayiksun.crm.common.error.ApiException;
import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.time.KstClock;
import com.dadayiksun.crm.customer.domain.Guardian;
import com.dadayiksun.crm.customer.domain.GuardianTag;
import com.dadayiksun.crm.customer.repository.GuardianRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 보호자 수정 (FR-CUS-008, POL-REG-008, NFR-DATA-003). */
class GuardianUpdateServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private GuardianRepository repo;
    private ChangeLogWriter log;
    private GuardianUpdateService service;
    private Guardian g;

    @BeforeEach
    void setUp() {
        repo = mock(GuardianRepository.class);
        log = mock(ChangeLogWriter.class);
        KstClock clock = new KstClock(Clock.fixed(Instant.parse("2026-11-26T01:00:00Z"), ZoneOffset.UTC));
        service = new GuardianUpdateService(repo, log, clock);
        g = Guardian.register("김보호", "01000000000", true, true, LocalDate.of(2025, 1, 1), null);
        ReflectionTestUtils.setField(g, "phoneStatus", "WRONG");
        when(repo.findById(7L)).thenReturn(Optional.of(g));
    }

    private JsonNode body(String s) throws Exception {
        return json.readTree(s);
    }

    @Test
    void 번호를_고치면_확인일이_오늘이고_상태는_OK() throws Exception {
        service.update(7L, body("{\"phone\":\"010-1111-2222\"}"));

        assertThat(g.getPhone()).isEqualTo("01011112222");
        assertThat(g.getPhoneCheckedAt()).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(g.getPhoneStatus()).isEqualTo("OK");
    }

    @Test
    void 같은_번호를_보내면_확인일은_그대로() throws Exception {
        service.update(7L, body("{\"phone\":\"010-0000-0000\"}"));

        assertThat(g.getPhoneCheckedAt()).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    void 태그를_붙이고_메모를_넣으면_TAG_CHANGE가_남는다() throws Exception {
        service.update(7L, body("{\"tags\":[\"CONTACT_TIME\",\"SENSITIVE\"],\"contactTimeNote\":\"오후 2시 이후\"}"));

        assertThat(g.tags()).containsExactlyInAnyOrder(GuardianTag.CONTACT_TIME, GuardianTag.SENSITIVE);
        assertThat(g.getContactTimeNote()).isEqualTo("오후 2시 이후");
        verify(log).write("TAG_CHANGE", "GUARDIAN", 7L, Map.of("tags", java.util.List.of()),
                Map.of("tags", java.util.List.of("SENSITIVE", "CONTACT_TIME")));
    }

    @Test
    void 연락_선호_시간_태그를_떼면_메모도_비운다() throws Exception {
        service.update(7L, body("{\"tags\":[\"CONTACT_TIME\"],\"contactTimeNote\":\"오전\"}"));
        service.update(7L, body("{\"tags\":[]}"));

        assertThat(g.getContactTimeNote()).isNull();
    }

    @Test
    void 메모는_null로_비울_수_있다() throws Exception {
        service.update(7L, body("{\"tags\":[\"CONTACT_TIME\"],\"contactTimeNote\":\"오전\"}"));
        service.update(7L, body("{\"contactTimeNote\":null}"));

        assertThat(g.getContactTimeNote()).isNull();
        assertThat(g.tags()).contains(GuardianTag.CONTACT_TIME);
    }

    @Test
    void 태그가_같으면_이력을_더_쌓지_않는다() throws Exception {
        service.update(7L, body("{\"name\":\"김보호\",\"tags\":[]}"));

        verify(log, never()).write(any(), any(), any(), any(), any());
    }

    @Test
    void 정해진_값_밖_태그는_400() {
        assertThatThrownBy(() -> service.update(7L, body("{\"tags\":[\"VIP\"]}")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(g.tags()).isEmpty();
    }

    @Test
    void 태그_없이_메모만_보내면_400() {
        assertThatThrownBy(() -> service.update(7L, body("{\"contactTimeNote\":\"오전\"}")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 빈_이름은_필수_문구로_400() {
        assertThatThrownBy(() -> service.update(7L, body("{\"name\":\" \"}")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("보호자 이름을(를) 입력해 주세요."));
    }

    @Test
    void 없는_보호자는_404() {
        assertThatThrownBy(() -> service.update(99L, json.createObjectNode()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
