import { describe, expect, it } from "vitest";
import { targetsHref, todayCards } from "./todayCards";

describe("todayCards", () => {
  it("summary 값을 다시 세지 않고 그대로 보여 준다", () => {
    const cards = todayCards({ today: { total: 12, done: 5 }, week: { total: 30, done: 7 }, missed: 3 });
    expect(cards.map((c) => [c.tab, c.value])).toEqual([
      ["today", "12마리 중 5마리 처리"],
      ["week", "30마리 중 7마리 처리"],
      ["missed", "3마리"],
    ]);
  });

  it("대상이 0이어도 숫자로 보인다", () => {
    const cards = todayCards({ today: { total: 0, done: 0 }, week: { total: 0, done: 0 }, missed: 0 });
    expect(cards.map((c) => c.value)).toEqual(["0마리 중 0마리 처리", "0마리 중 0마리 처리", "0마리"]);
  });

  it("카드는 연락 대상 목록의 같은 탭으로 간다", () => {
    expect(targetsHref("missed")).toBe("/targets?tab=missed");
  });
});
