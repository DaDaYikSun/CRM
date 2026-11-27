// S-02 오늘 할 일 카드 3개 (FR-DASH-001, 화면 설계 SCR-S02-02 ①).
// 숫자는 #152 summary 값 그대로 쓴다. 화면에서 다시 세지 않는다 (K17). 숫자는 모두 아이 수 (POL-MISS-009).
import type { TargetSummary, TargetTab } from "@/lib/api/target";

export interface TodayCard {
  tab: TargetTab;
  label: string;
  value: string;
  linkLabel: string;
}

/** 카드 순서와 이름 (로딩 중에도 이름은 보인다) */
export const CARD_LABEL: Record<TargetTab, string> = { today: "오늘 연락 대상", week: "이번 주(월~일)", missed: "누락" };
export const TODAY_CARD_TABS = Object.keys(CARD_LABEL) as TargetTab[];

/** 대상이 0이어도 숫자로 보인다 ('0마리 중 0마리 처리', #156 결정) */
export function todayCards(s: TargetSummary): TodayCard[] {
  return [
    { tab: "today", label: CARD_LABEL.today, value: `${s.today.total}마리 중 ${s.today.done}마리 처리`, linkLabel: "오늘 탭 열기" },
    { tab: "week", label: CARD_LABEL.week, value: `${s.week.total}마리 중 ${s.week.done}마리 처리`, linkLabel: "이번 주 탭 열기" },
    { tab: "missed", label: CARD_LABEL.missed, value: `${s.missed}마리`, linkLabel: "누락 탭 열기" },
  ];
}

/** 카드를 누르면 연락 대상 목록의 그 탭 (SCR-S02-07) */
export function targetsHref(tab: TargetTab): string {
  return `/targets?tab=${tab}`;
}
