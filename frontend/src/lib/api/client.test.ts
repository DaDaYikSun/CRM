import { describe, expect, it, vi } from "vitest";
import { ApiError, createClient, LOAD_FAILED_MESSAGE, SAVE_FAILED_MESSAGE } from "./client";

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

describe("client", () => {
  it("성공 응답의 data를 풀어 준다", async () => {
    const fetcher = vi.fn().mockResolvedValue(json(200, { data: { petId: 3 } }));
    await expect(createClient(fetcher).get("/api/pets/3")).resolves.toEqual({ petId: 3 });
  });

  it("오류 응답은 ApiError (code · message · fields)", async () => {
    const fetcher = vi.fn().mockResolvedValue(
      json(404, { error: { code: "NOT_FOUND", message: "찾는 정보가 없습니다. 목록에서 다시 찾아 주세요." } }),
    );
    const error = (await createClient(fetcher).get("/api/pets/9").catch((e) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe("NOT_FOUND");
    expect(fetcher).toHaveBeenCalledTimes(1); // 4xx는 다시 부르지 않음
  });

  it("조회 실패(5xx)는 자동으로 한 번 다시 부른다", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(new Response("", { status: 502 }))
      .mockResolvedValueOnce(json(200, { data: [] }));
    await expect(createClient(fetcher).get("/api/targets")).resolves.toEqual([]);
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("두 번 다 실패하면 조회 실패 문구", async () => {
    const fetcher = vi.fn().mockImplementation(async () => new Response("", { status: 500 }));
    const error = (await createClient(fetcher).get("/api/targets").catch((e) => e)) as ApiError;
    expect(error.message).toBe(LOAD_FAILED_MESSAGE);
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("저장 실패는 다시 부르지 않고, 저장 두 번 막기 키를 싣는다", async () => {
    const fetcher = vi.fn().mockImplementation(async () => new Response("", { status: 500 }));
    await createClient(fetcher)
      .post("/api/contacts", { memo: "" }, { idempotencyKey: "key-1" })
      .catch(() => undefined);
    const saveCalls = fetcher.mock.calls.filter(([path]) => path === "/api/contacts");
    expect(saveCalls).toHaveLength(1);
    expect(saveCalls[0][1].headers["Idempotency-Key"]).toBe("key-1");
  });

  it("조회 중 네트워크 오류도 자동으로 한 번 다시 부른다", async () => {
    const fetcher = vi
      .fn()
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockResolvedValueOnce(json(200, { data: [] }));
    await expect(createClient(fetcher).get("/api/targets")).resolves.toEqual([]);
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("저장 전 CSRF 쿠키를 못 받으면 저장 실패 ApiError로 알리고 저장하지 않는다", async () => {
    const fetcher = vi.fn().mockRejectedValue(new TypeError("Failed to fetch"));
    const error = (await createClient(fetcher).post("/api/contacts", {}).catch((e) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe("INTERNAL_ERROR");
    expect(error.message).toBe(SAVE_FAILED_MESSAGE);
    expect(fetcher.mock.calls.filter(([path]) => path === "/api/contacts")).toHaveLength(0);
  });
});
