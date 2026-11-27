import { describe, expect, it } from "vitest";
import type { TargetItem } from "@/lib/api/target";
import { itemLabel, scheduleState, stateBadge } from "./scheduleState";

const base: TargetItem = { scheduleId: 1, serviceType: "VACCINE", vaccineTypeName: "종합백신", dueDate: "2026-11-25", missed: false };
const TODAY = "2026-11-25";

describe("scheduleState", () => {
  it("저장된 누락 판정이 있으면 날짜와 상관없이 누락", () => {
    expect(scheduleState({ ...base, missed: true, dueDate: "2026-12-01" }, TODAY)).toBe("missed");
  });

  it("예정일이 오늘이면 오늘, 지났고 판정 전이면 예정 지남", () => {
    expect(scheduleState(base, TODAY)).toBe("today");
    expect(scheduleState({ ...base, dueDate: "2026-11-11" }, TODAY)).toBe("late");
    expect(scheduleState({ ...base, dueDate: "2026-11-29" }, TODAY)).toBe("normal");
  });

  it("배지 글자는 정책 4장 그대로", () => {
    expect(stateBadge(base, "today")).toBe("오늘 접종");
    expect(stateBadge({ ...base, serviceType: "GROOMING", vaccineTypeName: null }, "today")).toBe("오늘 미용");
    expect(stateBadge(base, "late")).toBe("예정 지남");
    expect(stateBadge(base, "missed")).toBe("누락");
    expect(stateBadge(base, "normal")).toBeNull();
  });

  it("미용 항목 이름은 미용", () => {
    expect(itemLabel({ ...base, serviceType: "GROOMING", vaccineTypeName: null })).toBe("미용");
    expect(itemLabel(base)).toBe("종합백신");
  });
});
