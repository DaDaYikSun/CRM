// 연락 패널 · 지난 연락 · 결과 저장 API (C13, API 설계 3-6). 공통 호출 함수만 쓴다.
import type { PageData } from "@/types/common";
import { api } from "./client";

export type ContactResult = "BOOKED" | "NO_ANSWER" | "REFUSED" | "OTHER_HOSPITAL" | "WRONG_NUMBER";
export type ContactMethod = "PHONE" | "SMS";
export type TalkedWith = "SELF" | "FAMILY";

export interface PanelItem {
  scheduleId: number;
  serviceType: "VACCINE" | "GROOMING";
  vaccineTypeName: string | null;
  dueDate: string; // YYYY-MM-DD
  missed: boolean; // 저장된 누락 판정이 있는지
  noAnswerCount: number; // 1이면 다시 '전화 안 받음'일 때 2일차 확인 창
}

export interface ContactPanel {
  guardian: {
    guardianId: number;
    name: string;
    phone: string; // 전체 번호 (이 패널과 보호자 상세에서만, POL-DATA-002)
    subPhone: string | null;
    preferredContact: ContactMethod | null;
    tags: string[];
    contactTimeNote: string | null;
    smsAgreed: boolean;
    phoneStatus: "OK" | "WRONG";
  };
  todayContact: { staffName: string; time: string } | null;
  pets: { petId: number; name: string; items: PanelItem[] }[];
}

/** 타임라인 CONTACT 줄 모양 + petName (API 설계 3-5 · 3-6) */
export interface ContactHistoryItem {
  kind: "CONTACT";
  id: number;
  date: string;
  summary: string; // "전화 · 본인 · 전화 안 받음"
  memo: string | null;
  staffName: string;
  recordedAt: string;
  edited: boolean;
  editable: boolean;
  converted: boolean;
  mockSms: boolean;
  petName: string;
  // S-06 표의 열을 나누려고 summary와 따로 준다 (화면 설계 SCR-S06-02, #223)
  method: ContactMethod;
  talkedWith: TalkedWith | null; // 문자면 없음
  result: ContactResult | null; // 모의 발송만 한 행이면 없음
  itemName: string; // 접종 종류 이름 또는 '미용'
}

export interface SaveContactInput {
  guardianId: number;
  method: ContactMethod;
  talkedWith: TalkedWith | null;
  memo: string | null;
  nextContactDate: string | null;
  items: { scheduleId: number; result: ContactResult }[];
  applyToHousehold: false; // 같은 집 일괄 적용은 초안 29
}

export interface SaveContactResult {
  contactGroupId: string;
  followUp: unknown; // #154가 채운다
}

export function getContactPanel(guardianId: number): Promise<ContactPanel> {
  return api.get<ContactPanel>(`/api/guardians/${guardianId}/contact-panel`);
}

export function getContactHistory(guardianId: number, page: number): Promise<PageData<ContactHistoryItem>> {
  return api.get<PageData<ContactHistoryItem>>(`/api/guardians/${guardianId}/contacts?page=${page}`);
}

/** 폼 하나에 키 하나 (K15, frontend/README.md). 저장이 성공하면 다음 저장은 새 키 */
export function saveContact(input: SaveContactInput, idempotencyKey: string): Promise<SaveContactResult> {
  return api.post<SaveContactResult>("/api/contacts", input, { idempotencyKey });
}
