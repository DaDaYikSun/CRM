import { describe, expect, it } from "vitest";
import type { GroomingLine } from "@/lib/api/grooming";
import { filterLines, groomingBadge, intervalBasisLabel } from "./groomingRules";

const TODAY = "2026-11-25";

function line(over: Partial<GroomingLine>): GroomingLine {
  return {
    petId: 1, petName: "보리", breedName: "말티즈", coatTypeName: "장모", birthDate: "2023-05-02",
    guardianName: "김보호", phoneLast4: "1234", guardianTags: [], scheduleId: 1, dueDate: TODAY,
    missed: false, lastGroomDate: null, groomInterval: null, groomedToday: false, ...over,
  };
}

describe("groomingBadge (정책 4-2 상태 배지)", () => {
  it("오늘 기록했으면 미용 완료가 먼저", () => {
    expect(groomingBadge(line({ groomedToday: true, missed: true, dueDate: "2026-11-01" }), TODAY)).toBe("done");
  });
  it("누락은 저장된 판정만 본다", () => {
    expect(groomingBadge(line({ missed: true, dueDate: "2026-11-01" }), TODAY)).toBe("missed");
    expect(groomingBadge(line({ dueDate: "2026-11-01" }), TODAY)).toBe("late");
  });
  it("예정일이 오늘이면 오늘 미용, 미래면 배지 없음", () => {
    expect(groomingBadge(line({}), TODAY)).toBe("today");
    expect(groomingBadge(line({ dueDate: "2026-11-27" }), TODAY)).toBeNull();
    expect(groomingBadge(line({ dueDate: null }), TODAY)).toBeNull();
  });
});

describe("filterLines (아이 · 보호자 이름 · 번호 뒤 4자리)", () => {
  const lines = [
    line({ petId: 1, petName: "보리", guardianName: "김보호", phoneLast4: "1234" }),
    line({ petId: 2, petName: "콩 이", guardianName: "이하나", phoneLast4: "5678" }),
  ];
  it("같은 아이가 세 값 각각으로 찾아진다", () => {
    expect(filterLines(lines, "보리").map((l) => l.petId)).toEqual([1]);
    expect(filterLines(lines, "김보").map((l) => l.petId)).toEqual([1]);
    expect(filterLines(lines, "1234").map((l) => l.petId)).toEqual([1]);
  });
  it("공백은 무시하고 빈 검색어면 그대로", () => {
    expect(filterLines(lines, "콩이").map((l) => l.petId)).toEqual([2]);
    expect(filterLines(lines, "  ")).toHaveLength(2);
  });
});

describe("intervalBasisLabel (POL-CYC-002 · 003)", () => {
  it("중앙값 / 기본 간격", () => {
    expect(intervalBasisLabel("MEDIAN")).toBe("최근 미용 간격의 중앙값");
    expect(intervalBasisLabel("DEFAULT")).toBe("털 유형 기본 간격");
  });
});
