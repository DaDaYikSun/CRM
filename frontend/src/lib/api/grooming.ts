// 미용 예정 목록 API (S-11, API 설계 3장 GET /api/grooming [제안]). 공통 호출 함수만 쓴다.
import type { PageData } from "@/types/common";
import { api } from "./client";
import type { GuardianTagCode } from "./customer";

export type GroomingTab = "today" | "week";

/** 한 줄 = 아이 한 마리. 번호는 뒤 4자리만 (NFR-SEC-003) */
export interface GroomingLine {
  petId: number;
  petName: string;
  breedName: string;
  coatTypeName: string;
  birthDate: string; // 나이 표시용
  guardianName: string;
  phoneLast4: string;
  guardianTags: GuardianTagCode[];
  /** 오늘 미용으로 완료한 주기 또는 진행 중 미용 주기. 주기 없이 기록한 오늘 미용이면 null */
  scheduleId: number | null;
  dueDate: string | null;
  missed: boolean; // 저장된 누락 판정 (T13)
  lastGroomDate: string | null;
  groomInterval: { days: number; basis: "DEFAULT" | "MEDIAN" } | null;
  groomedToday: boolean;
}

export function getGrooming(tab: GroomingTab, page = 1): Promise<PageData<GroomingLine>> {
  return api.get<PageData<GroomingLine>>(`/api/grooming?tab=${tab}&page=${page}`);
}
