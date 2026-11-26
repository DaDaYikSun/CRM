// 백엔드 공통 호출 함수 (#142). 업무별 API 파일(lib/api/<업무>.ts)은 이 함수만 써서 부른다.
// - 공통 응답 풀기: { data } → data, { error } → ApiError (C1, API 설계 1-2)
// - 다시 시도: 조회(GET) 실패는 자동으로 한 번 다시 부른다. 저장 실패는 자동으로 다시 부르지 않는다 (POL-ERR-001 · 002, API 설계 1-5)
// - CSRF: 쿠키 XSRF-TOKEN 값을 헤더 X-XSRF-TOKEN에 싣는다. 쿠키가 없으면 먼저 GET /api/csrf (K10, backend/README.md)
// - 저장 두 번 막기: 저장 요청에 Idempotency-Key 헤더를 싣는다 (K15)
//   화면은 저장 버튼을 누를 때마다 새 키를 만들지 말고, 폼 하나에 키 하나를 만들어 저장이 끝날 때까지 같은 키를 쓴다.
//   (newIdempotencyKey()로 만들어 { idempotencyKey } 로 넘긴다. 넘기지 않으면 요청마다 새 키)

import type { ApiErrorBody, ErrorCode, FieldError } from "@/types/common";

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const IDEMPOTENCY_HEADER = "Idempotency-Key";

/** 정책 4-2 '저장 실패' · '조회 실패 (자동 재시도 뒤)' */
export const SAVE_FAILED_MESSAGE = "저장하지 못했습니다. 다시 시도해 주세요.";
export const LOAD_FAILED_MESSAGE = "불러오지 못했습니다. 다시 시도해 주세요.";

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: ErrorCode,
    message: string,
    readonly fields: FieldError[] = [],
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export interface SaveOptions {
  idempotencyKey?: string;
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const found = document.cookie.split("; ").find((part) => part.startsWith(`${name}=`));
  return found ? decodeURIComponent(found.slice(name.length + 1)) : null;
}

async function csrfToken(fetcher: typeof fetch): Promise<string | null> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) return existing;
  await fetcher("/api/csrf", { method: "GET", credentials: "same-origin" });
  return readCookie(CSRF_COOKIE);
}

function isErrorBody(body: unknown): body is ApiErrorBody {
  return typeof body === "object" && body !== null && "error" in body;
}

async function parse<T>(response: Response, method: Method): Promise<T> {
  let body: unknown = null;
  const text = await response.text();
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = null;
    }
  }
  if (response.ok) {
    return (body as { data: T } | null)?.data as T;
  }
  if (isErrorBody(body)) {
    const { code, message, fields } = body.error;
    throw new ApiError(response.status, code, message, fields ?? []);
  }
  throw new ApiError(
    response.status,
    "INTERNAL_ERROR",
    method === "GET" ? LOAD_FAILED_MESSAGE : SAVE_FAILED_MESSAGE,
  );
}

/** 자동으로 한 번 다시 부를 실패인가 (네트워크 오류 status 0 · 서버 오류 5xx). 4xx는 다시 불러도 같으므로 다시 부르지 않는다 */
function shouldRetryRead(error: unknown): boolean {
  if (error instanceof ApiError) return error.status === 0 || error.status >= 500;
  return true;
}

export function createClient(fetcher: typeof fetch = (...args) => fetch(...args)) {
  async function send<T>(method: Method, path: string, body?: unknown, options: SaveOptions = {}): Promise<T> {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (method !== "GET") {
      let token: string | null;
      try {
        token = await csrfToken(fetcher);
      } catch {
        // CSRF 쿠키를 못 받음: 저장 실패와 같게 알린다 (POL-ERR-002). 자동으로 다시 부르지 않는다
        throw new ApiError(0, "INTERNAL_ERROR", SAVE_FAILED_MESSAGE);
      }
      if (token) headers[CSRF_HEADER] = token;
      headers[IDEMPOTENCY_HEADER] = options.idempotencyKey ?? newIdempotencyKey();
    }
    let response: Response;
    try {
      response = await fetcher(path, {
        method,
        headers,
        credentials: "same-origin",
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch {
      throw new ApiError(0, "INTERNAL_ERROR", method === "GET" ? LOAD_FAILED_MESSAGE : SAVE_FAILED_MESSAGE);
    }
    return parse<T>(response, method);
  }

  return {
    /** 조회. 실패하면 자동으로 한 번 다시 부른다 (POL-ERR-001) */
    async get<T>(path: string): Promise<T> {
      try {
        return await send<T>("GET", path);
      } catch (error) {
        if (!shouldRetryRead(error)) throw error;
        return send<T>("GET", path);
      }
    },
    /** 저장. 실패해도 자동으로 다시 부르지 않는다 (POL-ERR-002) */
    post<T>(path: string, body?: unknown, options?: SaveOptions): Promise<T> {
      return send<T>("POST", path, body, options);
    },
    put<T>(path: string, body?: unknown, options?: SaveOptions): Promise<T> {
      return send<T>("PUT", path, body, options);
    },
    patch<T>(path: string, body?: unknown, options?: SaveOptions): Promise<T> {
      return send<T>("PATCH", path, body, options);
    },
  };
}

export const api = createClient();
