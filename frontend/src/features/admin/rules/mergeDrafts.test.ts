import { describe, expect, it } from "vitest";
import { mergeDrafts } from "./mergeDrafts";

interface Item {
  id: number;
  name: string;
}
const idOf = (i: Item) => i.id;
const toDraft = (i: Item) => ({ name: i.name });

describe("목록 다시 불러오기 때 입력 칸 맞추기 (PR #256 리뷰)", () => {
  it("고친 줄은 남기고 고치지 않은 줄은 새 값으로", () => {
    const prev = [{ id: 1, name: "말티즈" }, { id: 2, name: "푸들" }];
    const drafts = { 1: { name: "말티즈" }, 2: { name: "토이 푸들" } }; // 2만 고침 (아직 저장 안 함)
    const next = [{ id: 1, name: "말티즈 (수정됨)" }, { id: 2, name: "푸들" }, { id: 3, name: "비숑" }];
    expect(mergeDrafts(prev, next, drafts, idOf, toDraft)).toEqual({
      1: { name: "말티즈 (수정됨)" },
      2: { name: "토이 푸들" },
      3: { name: "비숑" },
    });
  });

  it("처음 불러올 때는 모두 서버 값", () => {
    expect(mergeDrafts(null, [{ id: 1, name: "말티즈" }], {}, idOf, toDraft)).toEqual({ 1: { name: "말티즈" } });
  });

  it("저장한 줄은 서버 값이 초안과 같아져 새 값으로 맞춰진다", () => {
    const prev = [{ id: 1, name: "말티즈" }];
    expect(mergeDrafts(prev, [{ id: 1, name: "말티" }], { 1: { name: "말티" } }, idOf, toDraft)).toEqual({ 1: { name: "말티" } });
  });
});
