"use client";

import { useCallback, useRef, useState } from "react";
import { newIdempotencyKey } from "@/lib/api/client";
import { saveFailure, type Failure } from "@/lib/api/failure";

// 저장 + 실패 + [다시 시도] (FR-COM-001, POL-ERR-002, K15, #168).
// - 입력 값은 화면 상태에 그대로 둔다. 이 훅은 입력을 건드리지 않는다
// - 자동으로 다시 부르지 않는다. [다시 시도]는 사람이 누른다
// - 저장 두 번 막기 키: 같은 입력을 다시 보내면 같은 키(서버가 첫 요청을 이미 저장했으면 같은 응답을 돌려준다),
//   입력이 바뀌었으면 새 키(응답을 잃은 뒤 고친 입력이 옛 응답을 받지 않게, PR #213 리뷰). 성공하면 새 키
//   서버는 본문을 비교하지 않으므로(backend/README.md) 입력을 두 번째 인자로 넘겨 이 훅이 비교한다
//
//   const { save, saving, failure } = useSave();
//   const onSubmit = () => save((key) => createGuardian(input, key), input).then((r) => r && router.push(...));
//   {failure && <ErrorState message={failure.message} onRetry={failure.retry ? onSubmit : undefined} retrying={saving} />}
export function useSave() {
  const key = useRef<string>(newIdempotencyKey());
  /** 지금 키로 보낸 입력 (JSON). 다른 입력이면 키를 새로 만든다 */
  const keyInput = useRef<string | null>(null);
  const busy = useRef(false);
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState<Failure | null>(null);

  /** 성공하면 결과, 실패하면 undefined (failure에 담긴다). 저장 중이면 아무것도 안 한다 */
  const save = useCallback(
    async <T>(request: (idempotencyKey: string) => Promise<T>, input?: unknown): Promise<T | undefined> => {
      if (busy.current) return undefined;
      const fingerprint = input === undefined ? null : JSON.stringify(input);
      if (keyInput.current !== null && fingerprint !== keyInput.current) key.current = newIdempotencyKey();
      keyInput.current = fingerprint;
      busy.current = true;
      setSaving(true);
      setFailure(null);
      try {
        const result = await request(key.current);
        key.current = newIdempotencyKey();
        keyInput.current = null;
        return result;
      } catch (error) {
        setFailure(saveFailure(error));
        return undefined;
      } finally {
        busy.current = false;
        setSaving(false);
      }
    },
    [],
  );

  const clearFailure = useCallback(() => setFailure(null), []);
  return { save, saving, failure, clearFailure };
}
