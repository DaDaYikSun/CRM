// 방문 기록 API (C8, API 설계 3-5). 화면은 S-09 (#148).
import { api } from "./client";

export type VisitServiceType = "VACCINE" | "GROOMING";

export interface RecordVisitInput {
  serviceType: VisitServiceType;
  vaccineTypeId: number | null;
  visitDate: string; // YYYY-MM-DD (한국 날짜)
  memo: string | null;
}

export interface RecordVisitResult {
  visitId: number;
  completedScheduleId: number | null;
  nextSchedule: { scheduleId: number; serviceType: VisitServiceType; vaccineTypeId: number | null; dueDate: string } | null;
  groomInterval: { days: number; basis: string } | null;
}

/** POST /api/pets/{petId}/visits. 키는 모달(폼) 하나에 하나 (K15) */
export function recordVisit(petId: number, input: RecordVisitInput, idempotencyKey: string): Promise<RecordVisitResult> {
  return api.post<RecordVisitResult>(`/api/pets/${petId}/visits`, input, { idempotencyKey });
}
