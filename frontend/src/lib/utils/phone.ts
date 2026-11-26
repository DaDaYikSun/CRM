// 전화번호는 숫자만 남겨 저장 · 비교한다 (DB 설계 1장, API 설계 1-4, FR-SRCH-001).

/** 하이픈 · 띄어쓰기 등을 지우고 숫자만 남긴다 */
export function normalizePhone(input: string): string {
  return input.replace(/\D/g, "");
}

/** 번호 뒤 4자리 (목록 · 검색 표시용, POL-DATA-002) */
export function phoneLast4(input: string): string {
  return normalizePhone(input).slice(-4);
}
