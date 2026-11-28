// 목록을 다시 불러올 때 입력 칸을 맞추는 규칙 (B-03 선택지, PR #256 리뷰).
// 직원이 고친 줄(이전 서버 값과 다른 초안)은 그대로 두고, 고치지 않은 줄 · 새 줄만 새 서버 값으로 채운다.
// 그래서 한 줄을 저장하고 다시 불러와도 다른 줄의 저장 안 한 입력이 사라지지 않는다.
export function mergeDrafts<T, D>(
  prevItems: T[] | null,
  nextItems: T[],
  drafts: Record<number, D>,
  idOf: (item: T) => number,
  toDraft: (item: T) => D,
): Record<number, D> {
  const prev = new Map((prevItems ?? []).map((item) => [idOf(item), item]));
  const key = (d: D) => JSON.stringify(d);
  return Object.fromEntries(
    nextItems.map((item) => {
      const id = idOf(item);
      const before = prev.get(id);
      const draft = drafts[id];
      const edited = before !== undefined && draft !== undefined && key(draft) !== key(toDraft(before));
      return [id, edited ? draft : toDraft(item)];
    }),
  );
}
