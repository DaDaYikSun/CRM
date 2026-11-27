package com.dadayiksun.crm.common.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.dadayiksun.crm.common.error.ErrorCode;
import com.dadayiksun.crm.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 저장 두 번 막기 (팀 결정 K15, POL-ERR-002, API 설계 1-5).
 *
 * <p>화면은 저장 요청(POST · PUT · PATCH)마다 새 키를 만들어 {@value #HEADER} 헤더에 싣는다
 * ({@code frontend/src/lib/api/client.ts}가 한다). 버튼을 두 번 누르거나 응답이 늦어 같은 요청이 다시 오면,
 * 서버는 처음 요청만 처리하고 두 번째 요청에는 처음 응답을 그대로 돌려준다.
 * 처음 요청이 아직 처리 중이면 끝날 때까지 기다렸다가 같은 응답을 준다. 기다려도 끝나지 않으면 저장하지 않고
 * 저장 실패(500 INTERNAL_ERROR, 정책 4-2 문구)로 답한다. 같은 키로 다시 보내면 그때는 처음 응답을 받는다 [제안].
 *
 * <p>같은 요청의 기준: 같은 세션 + 같은 메서드 · 주소 + 같은 키. 키는 {@link IdempotencyStore#TTL} 동안 기억한다.
 * 오류 응답(4xx · 5xx)은 기억하지 않는다. 4xx는 저장이 일어나지 않았으니, 입력을 고쳐 같은 키로 다시 보내면 새로 처리한다.
 * 5xx는 다시 저장할 수 있게. 헤더가 없는 요청은 그대로 처리한다.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    private static final Set<String> SAVE_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final Duration WAIT = Duration.ofSeconds(10);
    /** 오류 본문만 쓰는 용도 (날짜 칸 없음). 형식은 공통 {@link ErrorResponse}, 문구는 {@link ErrorCode} 한 곳 (K16 ②). */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final IdempotencyStore store;
    private final Duration wait;

    public IdempotencyFilter() {
        this(new IdempotencyStore(Clock.systemUTC()));
    }

    public IdempotencyFilter(IdempotencyStore store) {
        this(store, WAIT);
    }

    IdempotencyFilter(IdempotencyStore store, Duration wait) {
        this.store = store;
        this.wait = wait;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String key = request.getHeader(HEADER);
        return !SAVE_METHODS.contains(request.getMethod()) || key == null || key.isBlank();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String storeKey = scope(request) + "|" + request.getMethod() + "|" + request.getRequestURI()
                + "|" + request.getHeader(HEADER).trim();

        IdempotencyStore.Claim claim = store.claim(storeKey);
        if (!claim.first()) {
            IdempotencyStore.StoredResponse earlier = claim.await(wait);
            if (earlier != null) {
                replay(earlier, response);
                return;
            }
            // 처음 요청이 서버 오류로 끝났거나 너무 오래 걸림: 이 요청을 새로 처리한다
            claim = store.claim(storeKey);
            if (!claim.first()) {
                IdempotencyStore.StoredResponse again = claim.await(wait);
                if (again != null) {
                    replay(again, response);
                } else {
                    // 처음 요청이 아직 처리 중: 이 요청까지 저장하면 같은 기록이 두 번 생긴다 (K15)
                    inProgress(response);
                }
                return;
            }
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            chain.doFilter(request, wrapper);
            if (wrapper.getStatus() < 400) {
                store.complete(storeKey, new IdempotencyStore.StoredResponse(
                        wrapper.getStatus(), wrapper.getContentType(), wrapper.getContentAsByteArray()));
                stored = true;
            }
        } finally {
            if (!stored) {
                store.release(storeKey);
            }
            wrapper.copyBodyToResponse();
        }
    }

    private static String scope(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null ? session.getId() : "no-session";
    }

    private static void inProgress(HttpServletResponse response) throws IOException {
        // 처음 요청이 끝나지 않아 처리하지 않은 두 번째 요청: C1 형식, 정책 4-2 '저장 실패'
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        JSON.writeValue(response.getOutputStream(), ErrorResponse.of(code.name(), ErrorCode.internalErrorMessage(false)));
        response.flushBuffer();
    }

    private static void replay(IdempotencyStore.StoredResponse stored, HttpServletResponse response) throws IOException {
        response.setStatus(stored.status());
        if (stored.contentType() != null) {
            response.setContentType(stored.contentType());
        }
        response.getOutputStream().write(stored.body());
        response.flushBuffer();
    }
}
