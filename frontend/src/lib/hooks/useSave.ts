"use client";

import { useCallback, useRef, useState } from "react";
import { newIdempotencyKey } from "@/lib/api/client";
import { saveFailure, type Failure } from "@/lib/api/failure";

// 저장 + 실패 + [다시 시도] (FR-COM-001, POL-ERR-002, K15, #168).
// - 입력 값은 화면 상태에 그대로 둔다. 이 훅은 입력을 건드리지 않는다
// - 자동으로 다시 부르지 않는다. [다시 시도]는 사람이 누른다
// - 저장 두 번 막기 키는 폼 하나에 하나: 실패해도 같은 키로 다시 보내고, 성공하면 새 키로 바꾼다
//   (서버가 첫 요청을 이미 저장했으면 같은 키로 같은 응답을 돌려준다)
//
//   const { save, saving, failure } = useSave();
//   const onSubmit = () => save((key) => createGuardian(input, key)).then((r) => r && router.push(...));
//   {failure && <ErrorState message={failure.message} onRetry={failure.retry ? onSubmit : undefined} retrying={saving} />}
export function useSave() {
  const key = useRef<string>(newIdempotencyKey());
  const busy = useRef(false);
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState<Failure | null>(null);

  /** 성공하면 결과, 실패하면 undefined (failure에 담긴다). 저장 중이면 아무것도 안 한다 */
  const save = useCallback(async <T>(request: (idempotencyKey: string) => Promise<T>): Promise<T | undefined> => {
    if (busy.current) return undefined;
    busy.current = true;
    setSaving(true);
    setFailure(null);
    try {
      const result = await request(key.current);
      key.current = newIdempotencyKey();
      return result;
    } catch (error) {
      setFailure(saveFailure(error));
      return undefined;
    } finally {
      busy.current = false;
      setSaving(false);
    }
  }, []);

  const clearFailure = useCallback(() => setFailure(null), []);
  return { save, saving, failure, clearFailure };
}
