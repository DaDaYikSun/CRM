// 통합 검색 API (C7, API 설계 3-4). 공통 호출 함수만 쓴다.
import type { PageData } from "@/types/common";
import { api } from "./client";

export interface SearchItem {
  petId: number;
  petName: string;
  guardianId: number;
  guardianName: string;
  phoneLast4: string;
  breedName: string;
  birthDate: string; // YYYY-MM-DD. 나이는 화면이 한국 날짜로 계산한다
  birthDateEstimated: boolean;
  status: "ACTIVE" | "RAINBOW";
  siblingNames: string[];
}

export function searchPreview(q: string): Promise<SearchItem[]> {
  return api.get<SearchItem[]>(`/api/search/preview?q=${encodeURIComponent(q)}`);
}

export function searchPets(q: string, page: number): Promise<PageData<SearchItem>> {
  return api.get<PageData<SearchItem>>(`/api/search?q=${encodeURIComponent(q)}&page=${page}`);
}
