package com.dadayiksun.crm.common.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link IdempotencyFilter}가 쓰는 메모리 저장소. 서버가 한 대(Railway, K1)라 메모리로 충분하다.
 * 서버를 여러 대로 늘리면 DB · Redis로 바꿔야 한다.
 */
public class IdempotencyStore {

    /** 같은 키를 같은 요청으로 보는 시간. */
    public static final Duration TTL = Duration.ofMinutes(10);

    public record StoredResponse(int status, String contentType, byte[] body) {
    }

    /** first가 true면 이 요청이 처리한다. false면 {@link #await}로 처음 요청의 응답을 기다린다. */
    public record Claim(boolean first, CompletableFuture<StoredResponse> future) {

        public StoredResponse await(Duration timeout) {
            try {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException | TimeoutException e) {
                return null;
            }
        }
    }

    private record Entry(CompletableFuture<StoredResponse> future, Instant createdAt) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public IdempotencyStore(Clock clock) {
        this.clock = clock;
    }

    public Claim claim(String key) {
        removeExpired();
        CompletableFuture<StoredResponse> mine = new CompletableFuture<>();
        Entry existing = entries.putIfAbsent(key, new Entry(mine, clock.instant()));
        if (existing == null) {
            return new Claim(true, mine);
        }
        return new Claim(false, existing.future());
    }

    public void complete(String key, StoredResponse response) {
        Entry entry = entries.get(key);
        if (entry != null) {
            entry.future().complete(response);
        }
    }

    /** 처리하지 못했거나 오류로 끝난 요청: 기다리던 요청이 새로 처리할 수 있게 키를 지운다. */
    public void release(String key) {
        Entry entry = entries.remove(key);
        if (entry != null) {
            entry.future().complete(null);
        }
    }

    private void removeExpired() {
        Instant limit = clock.instant().minus(TTL);
        entries.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(limit) && e.getValue().future().isDone());
    }
}
