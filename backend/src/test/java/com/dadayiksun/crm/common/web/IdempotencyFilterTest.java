package com.dadayiksun.crm.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import jakarta.servlet.FilterChain;

/** 같은 저장 요청을 두 번 보내면 한 번만 처리한다 (K15, POL-ERR-002). */
class IdempotencyFilterTest {

    private IdempotencyFilter filter;
    private AtomicInteger handled;
    private FilterChain chain;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        filter = new IdempotencyFilter(new IdempotencyStore(Clock.systemUTC()));
        handled = new AtomicInteger();
        session = new MockHttpSession();
        chain = (req, res) -> {
            int n = handled.incrementAndGet();
            res.setContentType("application/json");
            res.getOutputStream().write(("{\"data\":{\"visitId\":" + n + "}}").getBytes(StandardCharsets.UTF_8));
        };
    }

    private MockHttpServletResponse send(String method, String uri, String key) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setSession(session);
        if (key != null) {
            request.addHeader(IdempotencyFilter.HEADER, key);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void 같은_키로_두_번_보내면_한_번만_처리하고_같은_응답() throws Exception {
        MockHttpServletResponse first = send("POST", "/api/pets/1/visits", "key-1");
        MockHttpServletResponse second = send("POST", "/api/pets/1/visits", "key-1");

        assertThat(handled.get()).isEqualTo(1);
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(second.getStatus()).isEqualTo(first.getStatus());
    }

    @Test
    void 키가_다르면_각각_처리() throws Exception {
        send("POST", "/api/pets/1/visits", "key-1");
        send("POST", "/api/pets/1/visits", "key-2");
        assertThat(handled.get()).isEqualTo(2);
    }

    @Test
    void 키가_없거나_조회면_그대로_처리() throws Exception {
        send("POST", "/api/pets/1/visits", null);
        send("POST", "/api/pets/1/visits", null);
        send("GET", "/api/pets/1", "key-1");
        send("GET", "/api/pets/1", "key-1");
        assertThat(handled.get()).isEqualTo(4);
    }

    @Test
    void 서버_오류는_기억하지_않아_다시_저장할_수_있다() throws Exception {
        FilterChain failing = (req, res) -> {
            handled.incrementAndGet();
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(500);
        };
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/contacts");
        request.setSession(session);
        request.addHeader(IdempotencyFilter.HEADER, "key-9");
        filter.doFilter(request, new MockHttpServletResponse(), failing);

        send("POST", "/api/contacts", "key-9");
        assertThat(handled.get()).isEqualTo(2);
    }

    @Test
    void 입력_오류_4xx는_기억하지_않아_고쳐서_같은_키로_다시_보내면_처리한다() throws Exception {
        FilterChain invalid = (req, res) -> {
            handled.incrementAndGet();
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(400);
        };
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/guardians");
        request.setSession(session);
        request.addHeader(IdempotencyFilter.HEADER, "key-4");
        filter.doFilter(request, new MockHttpServletResponse(), invalid);

        MockHttpServletResponse fixed = send("POST", "/api/guardians", "key-4");
        assertThat(handled.get()).isEqualTo(2);
        assertThat(fixed.getStatus()).isEqualTo(200);
    }

    @Test
    void 처음_요청이_끝나지_않으면_두_번째_요청은_저장하지_않고_저장_실패로_답한다() throws Exception {
        IdempotencyStore store = new IdempotencyStore(Clock.systemUTC());
        filter = new IdempotencyFilter(store, Duration.ofMillis(50));
        // 처음 요청이 처리 중인 상태 (끝나지 않음)
        store.claim(session.getId() + "|POST|/api/pets/1/visits|key-slow");

        MockHttpServletResponse second = send("POST", "/api/pets/1/visits", "key-slow");

        assertThat(handled.get()).isZero();
        assertThat(second.getStatus()).isEqualTo(500);
        assertThat(second.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"INTERNAL_ERROR\"").contains("저장하지 못했습니다. 다시 시도해 주세요.");
    }
}
