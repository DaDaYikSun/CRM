// 연락 대상 API (C11, API 설계 3-6). 공통 호출 함수만 쓴다.
import type { PageData } from "@/types/common";
import { api } from "./client";

export type TargetTab = "today" | "week" | "missed";

export interface TargetItem {
  scheduleId: number;
  serviceType: "VACCINE" | "GROOMING";
  vaccineTypeName: string | null;
  dueDate: string; // YYYY-MM-DD
  missed: boolean; // 저장된 누락 판정이 있는지
}

export interface TargetPet {
  petId: number;
  name: string;
  items: TargetItem[]; // 가장 가까운 접종 하나 + 미용
  moreItems: TargetItem[]; // 펼치면 보일 나머지 접종
}

/** 한 줄 = 보호자 한 명. preferredContact · tags · contactTimeNote · nextContactDate · processedToday는 FR-TGT-004(#173)가 채운다 */
export interface TargetLine {
  guardianId: number;
  guardianName: string;
  phoneLast4: string;
  preferredContact: "PHONE" | "SMS" | null;
  tags: string[] | null;
  contactTimeNote: string | null;
  nextContactDate: string | null;
  processedToday: { staffName: string; time: string } | null;
  pets: TargetPet[];
}

export interface TargetSummary {
  today: { total: number; done: number };
  week: { total: number; done: number };
  missed: number;
}

export function getTargets(tab: TargetTab, page: number): Promise<PageData<TargetLine>> {
  return api.get<PageData<TargetLine>>(`/api/targets?tab=${tab}&page=${page}`);
}

export function getTargetSummary(): Promise<TargetSummary> {
  return api.get<TargetSummary>("/api/targets/summary");
}
