import { describe, expect, it } from "vitest";
import type { PanelItem } from "@/lib/api/contact";
import { canSave, defaultNextContactDate, formatPhone, isSecondDayNoAnswer, secondDayCopy } from "./contactRules";

const item = (scheduleId: number, noAnswerCount = 0): PanelItem => ({
  scheduleId,
  serviceType: "VACCINE",
  vaccineTypeName: "종합백신",
  dueDate: "2026-11-24",
  missed: false,
  noAnswerCount,
});

describe("2일차 확인 창", () => {
  it("어제 안 받은 항목에 다시 '전화 안 받음'을 고르면 띄운다", () => {
    expect(isSecondDayNoAnswer([item(1, 1), item(2)], { 1: "NO_ANSWER", 2: "NO_ANSWER" })).toBe(true);
  });

  it("처음 안 받은 항목이거나 다른 결과면 띄우지 않는다", () => {
    expect(isSecondDayNoAnswer([item(1, 0)], { 1: "NO_ANSWER" })).toBe(false);
    expect(isSecondDayNoAnswer([item(1, 1)], { 1: "BOOKED" })).toBe(false);
  });

  it("수신 동의가 없으면 문자를 보낸다는 문구를 쓰지 않는다", () => {
    const copy = secondDayCopy(false);
    expect(copy.detail).toBeNull();
    expect([copy.notice, copy.confirm].join(" ")).not.toMatch(/모의 발송|문자 보내/);
    expect(secondDayCopy(true).confirm).toBe("문자 보내고 마치기");
  });
});

describe("입력 기본값 · 저장 가능", () => {
  it("'전화 안 받음'이 있으면 다음 연락 예정일은 내일(한국 날짜 기준)", () => {
    expect(defaultNextContactDate({ 1: "NO_ANSWER" }, "2026-11-30")).toBe("2026-12-01");
    expect(defaultNextContactDate({ 1: "BOOKED" }, "2026-11-30")).toBe("");
  });

  it("모든 항목에 결과가 있고 메모가 200자 이하일 때만 저장할 수 있다", () => {
    expect(canSave([item(1), item(2)], { 1: "BOOKED" }, "")).toBe(false);
    expect(canSave([item(1), item(2)], { 1: "BOOKED", 2: "REFUSED" }, "가".repeat(200))).toBe(true);
    expect(canSave([item(1)], { 1: "BOOKED" }, "가".repeat(201))).toBe(false);
    expect(canSave([], {}, "")).toBe(false);
  });

  it("번호는 하이픈을 넣어 보인다", () => {
    expect(formatPhone("01000000000")).toBe("010-0000-0000");
    expect(formatPhone("0212345678")).toBe("02-1234-5678");
  });
});
