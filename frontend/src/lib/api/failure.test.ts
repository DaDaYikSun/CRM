import { describe, expect, it } from "vitest";
import { ApiError, LOAD_FAILED_MESSAGE, SAVE_FAILED_MESSAGE } from "./client";
import { loadFailure, saveFailure } from "./failure";

const FORBIDDEN = "이 작업을 할 권한이 없습니다. 필요하면 관리자에게 문의해 주세요.";

describe("loadFailure", () => {
  it("네트워크 · 서버 오류는 조회 실패 문구 + 다시 시도", () => {
    expect(loadFailure(new ApiError(0, "INTERNAL_ERROR", LOAD_FAILED_MESSAGE))).toEqual({
      message: LOAD_FAILED_MESSAGE,
      retry: true,
      fields: [],
    });
    expect(loadFailure(new ApiError(503, "INTERNAL_ERROR", "x")).message).toBe(LOAD_FAILED_MESSAGE);
    expect(loadFailure(new TypeError("boom")).retry).toBe(true);
  });
  it("권한 없음은 서버 문구, 다시 시도 없음", () => {
    expect(loadFailure(new ApiError(403, "FORBIDDEN", FORBIDDEN))).toEqual({ message: FORBIDDEN, retry: false, fields: [] });
  });
});

describe("saveFailure", () => {
  it("네트워크 · 서버 오류는 저장 실패 문구 + 다시 시도", () => {
    expect(saveFailure(new ApiError(0, "INTERNAL_ERROR", SAVE_FAILED_MESSAGE))).toEqual({
      message: SAVE_FAILED_MESSAGE,
      retry: true,
      fields: [],
    });
    expect(saveFailure(new ApiError(500, "INTERNAL_ERROR", "서버 오류")).message).toBe(SAVE_FAILED_MESSAGE);
  });
  it("입력 오류는 서버 문구와 칸 오류, 다시 시도 없음", () => {
    const fields = [{ field: "name", message: "이름을(를) 입력해 주세요." }];
    expect(saveFailure(new ApiError(400, "VALIDATION_FAILED", "입력을 확인해 주세요.", fields))).toEqual({
      message: "입력을 확인해 주세요.",
      retry: false,
      fields,
    });
  });
});
