// C13 GET /api/guardians/{id}/contacts?page= (API 설계 3-6). #153의 lib/api/contact.ts가 머지되면 그 함수로 바꾸고 이 파일을 지운다.
import { api } from "@/lib/api/client";
import type { PageData } from "@/types/common";
import type { GuardianContactItem } from "./contactHistoryRules";

export function fetchGuardianContacts(guardianId: number, page: number): Promise<PageData<GuardianContactItem>> {
  return api.get<PageData<GuardianContactItem>>(`/api/guardians/${guardianId}/contacts?page=${page}`);
}
