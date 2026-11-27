// 대시보드 지표 API (C15, API 설계 3-8). 공통 호출 함수만 쓴다.
import type { ContactResult } from "./contact";
import { api } from "./client";

/** 비율 · 분자 · 분모. 분모가 0이면 rate = null (POL-ERR-008) */
export interface Ratio {
  rate: number | null;
  numerator: number;
  denominator: number;
}

export interface Conversion {
  rate: number | null;
  converted: number;
  total: number;
}

export type ContactMethod = "PHONE" | "SMS";

export interface Metrics {
  from: string; // YYYY-MM-DD
  to: string;
  revisitRate: { vaccine: Ratio; grooming: Ratio };
  vaccineMissRate: Ratio;
  /** 예정일 대비 실제 방문 차이(일)의 평균, 서비스별 (#157) */
  avgGapDays: { vaccine: number | null; grooming: number | null };
  conversionByResult: Record<ContactResult, Conversion>;
  conversionByMethod: Record<ContactMethod, Conversion>;
}

/** 기간을 비우면 서버가 이번 달(한국 시간)로 센다 (SCR-S02-03). 기간 선택은 #176 */
export function getMetrics(from?: string, to?: string): Promise<Metrics> {
  const q = new URLSearchParams();
  if (from) q.set("from", from);
  if (to) q.set("to", to);
  const query = q.toString();
  return api.get<Metrics>(`/api/dashboard/metrics${query ? `?${query}` : ""}`);
}
