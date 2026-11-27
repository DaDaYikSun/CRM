"use client";

import { useCallback, useEffect, useState, type DependencyList } from "react";
import { loadFailure, type Failure } from "@/lib/api/failure";

// 조회 + 실패 + [다시 시도] (POL-ERR-001, #168). 자동 한 번 다시 부르기는 api.get이 한다.
//   const { data, failure, retry } = useQueryWithRetry(() => getTargets(tab, page), [tab, page]);
//   {failure && <ErrorState message={failure.message} onRetry={failure.retry ? retry : undefined} />}
// deps가 바뀌거나 retry를 누르면 다시 부른다. 늦게 온 이전 응답은 버린다.
export function useQueryWithRetry<T>(load: () => Promise<T>, deps: DependencyList) {
  const [data, setData] = useState<T | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let stale = false;
    setLoading(true);
    setFailure(null);
    load()
      .then((value) => {
        if (stale) return;
        setData(value);
        setLoading(false);
      })
      .catch((error: unknown) => {
        if (stale) return;
        setData(null);
        setFailure(loadFailure(error));
        setLoading(false);
      });
    return () => {
      stale = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 부르는 쪽이 넘긴 deps와 [다시 시도]로만 다시 부른다
  }, [...deps, attempt]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);
  return { data, failure, loading, retry };
}
