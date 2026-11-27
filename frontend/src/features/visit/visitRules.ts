// S-09 방문 기록 모달 규칙 (FR-VIS-001 · 002, 화면 설계 SCR-S09). 문구는 정책 4-2 · 4-4 그대로.
import type { VisitServiceType } from "@/lib/api/visit";

export const MEMO_MAX = 200; // 서버 VisitService.MEMO_MAX · visit.memo VARCHAR(200)과 같다 (POL-DATA-010)

export const VISIT_COPY = {
  guide: {
    VACCINE: "저장하면 이 접종의 다음 예정일을 규칙 표로 다시 계산합니다.", // 정책 4-4 S-09 접종 저장
    GROOMING: "기록하면 다음 미용 예정일을 다시 계산합니다.", // 정책 4-4 S-09 미용 저장
  },
  memoGuide: "진료 내용·민감한 정보·감정 표현은 적지 않습니다. (200자까지)", // 정책 4-2
} as const;

const SERVICE_LABEL: Record<VisitServiceType, string> = { VACCINE: "접종", GROOMING: "미용" };

/** 정책 4-2 저장 · 완료 안내 "{접종/미용} 기록을 저장했습니다. 다음 예정일을 다시 계산했습니다." */
export function savedMessage(service: VisitServiceType): string {
  return `${SERVICE_LABEL[service]} 기록을 저장했습니다. 다음 예정일을 다시 계산했습니다.`;
}

/** 필수가 빠지면 [저장]이 꺼진다 (SCR-S09-03): 접종은 종류, 둘 다 날짜. 오늘 뒤 날짜는 막는다 (#148 결정) */
export function canSave(
  service: VisitServiceType,
  vaccineTypeId: string,
  visitDate: string,
  memo: string,
  today: string,
): boolean {
  if (service === "VACCINE" && !vaccineTypeId) return false;
  return visitDate !== "" && visitDate <= today && memo.length <= MEMO_MAX;
}

/**
 * 입력한 것이 있어 닫으면 사라지는지 (나가기 경고, FR-COM-003, #243). 처음 연 상태(구분 · 오늘 날짜 · 빈 칸)와 다르면 true.
 * @param initialService 모달을 연 구분 (미용사는 늘 미용)
 */
export function isVisitDirty(
  input: { service: VisitServiceType; vaccineTypeId: string; visitDate: string; memo: string },
  initialService: VisitServiceType,
  today: string,
): boolean {
  return (
    input.service !== initialService || input.vaccineTypeId !== "" || input.visitDate !== today || input.memo.trim() !== ""
  );
}
