// S-05 결과 없음 → S-08 주소 (FR-SRCH-004). 검색어가 번호(숫자만 남는 값)면 phone, 아니면 name.
export function registerHref(q: string): string {
  const text = q.replace(/[\s-]/g, "");
  return /^\d+$/.test(text)
    ? `/register?phone=${encodeURIComponent(text)}`
    : `/register?name=${encodeURIComponent(q.trim())}`;
}
