// 무지개다리로 표시 확인 모달 문구 (FR-CUS-007, 정책 4-2 '무지개다리 변경'). {보류 기간}은 B-03 기준값 (팀 결정 T6).
export function rainbowConfirmMessage(petName: string, holdDays: number): string {
  return `${petName}를 무지개다리로 표시하면 모든 연락 대상과 통계에서 빠집니다. 같은 집 다른 아이는 ${holdDays}일 동안 연락을 보류합니다.`;
}

/** 보류 마감 당일까지 보류가 유지된다 (API 설계 3-3, DB 5-2). today · until은 'YYYY-MM-DD' */
export function isHeld(until: string | null, today: string): until is string {
  return until !== null && until >= today;
}
