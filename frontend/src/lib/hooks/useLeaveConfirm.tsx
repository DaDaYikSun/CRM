"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { LeaveConfirm } from "@/components/ui/LeaveConfirm";
import { leaveTarget } from "./leaveTarget";

// 입력 중 나가기 경고 (FR-COM-003, 정책 4-2 '나가기 경고', CMP-S07, #184).
//   const { leaveDialog, confirmLeave } = useLeaveConfirm(isDirty);
//   <Button onClick={() => confirmLeave(onCancel)}>취소</Button>
//   {leaveDialog}
// isDirty가 true인 동안:
// - 사이드바 · 위쪽 바 · 화면 안의 링크를 누르면 확인 창을 띄우고, [나가기]를 누르면 그 주소로 간다
// - 새로 고침 · 탭 닫기 · 다른 주소 입력은 브라우저가 자기 확인 창을 띄운다 (beforeunload. 문구는 브라우저가 정해 바꿀 수 없다)
// - [취소] · 패널 닫기처럼 화면이 직접 나가는 동작은 confirmLeave(동작)으로 감싼다
// 브라우저 [뒤로]는 막지 않는다 (Next.js App Router가 막는 방법을 주지 않는다) [?].
// 저장이 끝나면 isDirty가 false가 되도록 부르는 쪽이 상태를 맞춘다.
export function useLeaveConfirm(isDirty: boolean): { leaveDialog: ReactNode; confirmLeave: (action: () => void) => void } {
  const router = useRouter();
  const [pending, setPending] = useState<(() => void) | null>(null);
  const dirty = useRef(isDirty);
  dirty.current = isDirty;

  // 확인 창이 떠 있는 동안 저장이 끝나 입력이 없어지면 창을 닫는다 (잃을 것이 없다)
  useEffect(() => {
    if (!isDirty) setPending(null);
  }, [isDirty]);

  useEffect(() => {
    if (!isDirty) return;
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirty.current) return; // [나가기]로 이미 승인한 이동(예: router.back())은 브라우저가 다시 묻지 않게
      event.preventDefault();
      event.returnValue = ""; // 옛 브라우저용
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [isDirty]);

  useEffect(() => {
    if (!isDirty) return;
    // 캡처 단계에서 먼저 받아 Next.js <Link>의 이동보다 앞에서 멈춘다
    const onClick = (event: MouseEvent) => {
      const anchor = (event.target as Element | null)?.closest?.("a[href]");
      if (!(anchor instanceof HTMLAnchorElement)) return;
      const href = leaveTarget(
        {
          href: anchor.getAttribute("href"),
          target: anchor.getAttribute("target"),
          download: anchor.hasAttribute("download"),
          button: event.button,
          modified: event.ctrlKey || event.metaKey || event.shiftKey || event.altKey,
        },
        `${window.location.pathname}${window.location.search}`,
        window.location.origin,
      );
      if (!href) return;
      event.preventDefault();
      event.stopPropagation();
      setPending(() => () => router.push(href));
    };
    document.addEventListener("click", onClick, true);
    return () => document.removeEventListener("click", onClick, true);
  }, [isDirty, router]);

  const confirmLeave = useCallback((action: () => void) => {
    if (dirty.current) setPending(() => action);
    else action();
  }, []);

  const stay = useCallback(() => setPending(null), []);
  const leave = useCallback(() => {
    const action = pending;
    setPending(null);
    dirty.current = false;
    action?.();
  }, [pending]);

  return { leaveDialog: <LeaveConfirm open={pending !== null} onStay={stay} onLeave={leave} />, confirmLeave };
}
