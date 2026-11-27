import { describe, expect, it } from "vitest";
import type { Metrics } from "@/lib/api/dashboard";
import { gapText, hasContacts, methodRows, percent, resultRows, revisitCards } from "./metrics";

const zero = { rate: null, converted: 0, total: 0 };
function metrics(over: Partial<Metrics> = {}): Metrics {
  return {
    from: "2026-11-01",
    to: "2026-11-30",
    revisitRate: {
      vaccine: { rate: 0.62, numerator: 31, denominator: 50 },
      grooming: { rate: null, numerator: 0, denominator: 0 },
    },
    vaccineMissRate: { rate: 0.38, numerator: 19, denominator: 50 },
    avgGapDays: { vaccine: 6.4, grooming: null },
    conversionByResult: {
      BOOKED: { rate: 0.8, converted: 16, total: 20 },
      NO_ANSWER: zero,
      REFUSED: zero,
      OTHER_HOSPITAL: zero,
      WRONG_NUMBER: zero,
    },
    conversionByMethod: { PHONE: { rate: 0.45, converted: 19, total: 42 }, SMS: zero },
    ...over,
  };
}

describe("재방문 지표 (SCR-S02-02 ②)", () => {
  it("비율은 % 와 아이 수 분자 / 분모로, 분모 0은 빈 상태", () => {
    const cards = revisitCards(metrics());
    expect(cards.map((c) => c.label)).toEqual([
      "접종 재방문율 (주요 접종)",
      "미용 재방문율",
      "접종 누락률 (주요 접종)",
      "예정일 대비 실제 방문 차이",
    ]);
    expect(cards[0]).toMatchObject({ value: "62%", detail: "31마리 / 50마리" });
    expect(cards[1]).toMatchObject({ value: null });
    expect(cards[3].value).toBe("접종 +6.4일");
  });

  it("방문 차이는 서비스별, 둘 다 없으면 빈 상태", () => {
    expect(revisitCards(metrics({ avgGapDays: { vaccine: null, grooming: null } }))[3].value).toBeNull();
    expect(gapText(-1.2)).toBe("-1.2일");
    expect(gapText(0)).toBe("0일");
    expect(percent(0.7097)).toBe("71%");
    expect(percent(0.1234)).toBe("12.3%");
  });
});

describe("연락 효과 (SCR-S02-02 ③)", () => {
  it("결과 유형 5가지 순서대로, 0건은 빈 상태", () => {
    const rows = resultRows(metrics());
    expect(rows.map((r) => r.label)).toEqual(["예약함", "전화 안 받음", "거절", "다른 병원 다님", "번호 오류"]);
    expect(rows[0]).toEqual({ label: "예약함", rate: "80%", count: "16건 / 20건" });
    expect(rows[2].rate).toBeNull();
  });

  it("연락 방법별, 연락이 없으면 표 대신 빈 상태", () => {
    expect(methodRows(metrics()).map((r) => r.label)).toEqual(["전화", "문자 (모의 발송)"]);
    expect(hasContacts(metrics())).toBe(true);
    expect(hasContacts(metrics({ conversionByMethod: { PHONE: zero, SMS: zero } }))).toBe(false);
  });
});
