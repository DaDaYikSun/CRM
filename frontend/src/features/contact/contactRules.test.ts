import { describe, expect, it } from "vitest";
import type { PanelItem } from "@/lib/api/contact";
import { canSave, defaultNextContactDate, formatPhone, isContactDirty, isNightKst, isSecondDayNoAnswer, savedToast, secondDayCopy } from "./contactRules";

const TODAY = "2026-11-25";

const item = (scheduleId: number, noAnswerCount = 0, nextContactDate: string | null = noAnswerCount ? TODAY : null): PanelItem => ({
  scheduleId,
  serviceType: "VACCINE",
  vaccineTypeName: "종합백신",
  dueDate: "2026-11-24",
  missed: false,
  noAnswerCount,
  nextContactDate,
});

describe("2일차 확인 창", () => {
  it("어제 안 받은 항목에 다시 '전화 안 받음'을 고르면 띄운다", () => {
    expect(isSecondDayNoAnswer([item(1, 1), item(2)], { 1: "NO_ANSWER", 2: "NO_ANSWER" }, TODAY)).toBe(true);
  });

  it("같은 날 다시 안 받으면(다음 연락 예정일이 내일) 띄우지 않는다", () => {
    expect(isSecondDayNoAnswer([item(1, 1, "2026-11-26")], { 1: "NO_ANSWER" }, TODAY)).toBe(false);
  });

  it("처음 안 받은 항목이거나 다른 결과면 띄우지 않는다", () => {
    expect(isSecondDayNoAnswer([item(1, 0)], { 1: "NO_ANSWER" }, TODAY)).toBe(false);
    expect(isSecondDayNoAnswer([item(1, 1)], { 1: "BOOKED" }, TODAY)).toBe(false);
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

describe("savedToast (정책 4-2, #154)", () => {
  it("끝낸 주기가 없으면 저장 성공 문구", () => {
    expect(savedToast({ closedScheduleIds: [], mockSms: null })).toBe("연락 결과를 저장했습니다.");
  });

  it("2일차 · 문자 보냄이면 마침 토스트 하나만", () => {
    expect(savedToast({ closedScheduleIds: [101], mockSms: { sent: true, template: "VACCINE" } })).toBe(
      "문자를 보낸 것으로 기록하고 이번 주기 연락을 마쳤습니다 (모의 발송).",
    );
  });

  it("2일차 · 수신 동의 없음이면 문자 없이 마침", () => {
    expect(savedToast({ closedScheduleIds: [101], mockSms: null })).toBe("문자 없이 이번 주기 연락을 마쳤습니다.");
  });

  it("2일차 · 문자를 못 보냈으면 발송 실패 (2일차)", () => {
    expect(savedToast({ closedScheduleIds: [101], mockSms: { sent: false, template: "VACCINE" } })).toBe(
      "연락 결과는 저장했습니다. 안내 문자는 보내지 못했습니다. 필요하면 보호자에게 직접 연락해 주세요.",
    );
  });
});

describe("isNightKst (POL-CNT-007, 한국 시간 21시~다음 날 8시)", () => {
  const kst = (hhmm: string) => new Date(`2026-11-25T${hhmm}:00+09:00`);
  it("20:59와 08:00은 낮, 21:00과 07:59는 밤", () => {
    expect(isNightKst(kst("20:59"))).toBe(false);
    expect(isNightKst(kst("21:00"))).toBe(true);
    expect(isNightKst(kst("07:59"))).toBe(true);
    expect(isNightKst(kst("08:00"))).toBe(false);
  });
  it("자정(00:00)은 밤", () => {
    expect(isNightKst(kst("00:00"))).toBe(true);
  });
});

describe("isContactDirty (나가기 경고, #243)", () => {
  it("아무것도 고르지 않았으면 false (공백 메모도)", () => {
    expect(isContactDirty({}, "", false)).toBe(false);
    expect(isContactDirty({ 101: undefined }, "  ", false)).toBe(false);
  });
  it("결과 · 메모 · 다음 연락 예정일 중 하나라도 있으면 true", () => {
    expect(isContactDirty({ 101: "BOOKED" }, "", false)).toBe(true);
    expect(isContactDirty({}, "오후에 다시", false)).toBe(true);
    expect(isContactDirty({}, "", true)).toBe(true);
  });
});
