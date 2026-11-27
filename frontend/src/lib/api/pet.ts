// 아이 상세 · 통합 타임라인 API (C9, API 설계 3-3 · 3-5). 공통 호출 함수만 쓴다.
import type { PageData } from "@/types/common";
import { api } from "./client";
import type { GuardianTagCode } from "./customer";

export interface PetDetailResponse {
  pet: {
    petId: number;
    name: string;
    species: "DOG";
    status: "ACTIVE" | "RAINBOW";
    breed: { id: number; name: string };
    coatType: { id: number; name: string };
    birthDate: string;
    birthDateEstimated: boolean;
    weightKg: number | null;
    contactHoldUntil: string | null;
    groomInterval: { days: number; basis: "DEFAULT" | "MEDIAN" } | null;
  };
  /** 전체 번호는 오지 않는다 (NFR-SEC-003) */
  guardian: { guardianId: number; name: string; phoneLast4: string; tags: GuardianTagCode[] };
  siblings: { petId: number; name: string; status: "ACTIVE" | "RAINBOW" }[];
  /** 진행 중(OPEN) 주기만. missed는 저장된 판정 (T13) */
  schedules: {
    scheduleId: number;
    serviceType: "VACCINE" | "GROOMING";
    vaccineType: { vaccineTypeId: number; name: string; isMajor: boolean } | null;
    dueDate: string;
    missed: boolean;
  }[];
}

export type TimelineType = "ALL" | "VACCINE" | "GROOMING" | "CONTACT";

export interface TimelineLine {
  kind: "VACCINE" | "GROOMING" | "CONTACT";
  id: number;
  date: string;
  summary: string;
  memo: string | null;
  staffName: string;
  recordedAt: string;
  edited: boolean;
  editable: boolean;
  /** 연락 줄만 */
  converted?: boolean;
  mockSms?: boolean;
}

export function fetchPetDetail(petId: number): Promise<PetDetailResponse> {
  return api.get<PetDetailResponse>(`/api/pets/${petId}`);
}

/** type을 빼면 서버가 역할에 맞게 준다 (미용사는 미용 줄만, POL-ACC-011) */
export function fetchPetTimeline(petId: number, page: number, type?: TimelineType): Promise<PageData<TimelineLine>> {
  const query = type ? `type=${type}&page=${page}` : `page=${page}`;
  return api.get<PageData<TimelineLine>>(`/api/pets/${petId}/timeline?${query}`);
}
