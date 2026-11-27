// B-02 주기 규칙 · B-03 기준값 입력 검사와 문구 (#175, FR-ADM-002 · 003 · 004).
// 문구는 정책 정의서 4-2 · 4-4 그대로. 서버도 같은 검사를 한다 (POL-ERR-006).

export const RULE_COPY = {
  /** 4-2 RULE_VALUE_INVALID */
  invalid: "값이 비었거나 0 이하라 저장하지 못했습니다. 1 이상의 숫자를 입력해 주세요.",
  /** 4-2 주기 규칙 저장 (B-02) */
  rulesSaved: "저장했습니다. 아직 오지 않은 예정일을 다시 계산했습니다.",
  /** 4-2 기준값 저장 (B-03) */
  thresholdSaved: "저장했습니다. 이미 열린 주기와 걸려 있는 보류는 그대로 두고, 이제부터 새 값을 씁니다.",
  /** 4-4 B-02 저장 버튼 위 */
  rulesGuide: "규칙을 바꾸면 아직 오지 않은 예정일만 다시 계산합니다. 지난 예정일과 누락 판정·통계는 그대로 둡니다.",
  /** 4-4 B-02 주요 접종 표시 */
  majorGuide: "'주요 접종'만 접종 재방문율과 접종 누락률에 들어갑니다",
  /** 4-4 B-03 누락 기준 */
  missGuide:
    "예정일 + 이 날짜가 지나도록 방문이 없으면 누락으로 판정합니다. 바꾸면 이제부터 만드는 주기에 쓰고, 전환 인정 기간도 같이 따라갑니다",
  /** 4-4 B-03 연락 보류 기간 */
  holdGuide: "같은 집 다른 아이를 이 기간 동안 연락 대상에서 뺍니다",
} as const;

/** 입력 칸 글자 → 숫자. 비었으면 null, 정수가 아니면 NaN */
export function toInt(text: string): number | null {
  const trimmed = text.trim();
  if (trimmed === "") return null;
  return /^-?\d+$/.test(trimmed) ? Number(trimmed) : Number.NaN;
}

/**
 * 칸 하나가 틀렸는지. 비었거나 0 이하 · 정수가 아니면 틀림.
 * optional 칸(끝 나이 · 횟수)은 비어도 된다: '끝 없음' · '계속 반복' (DB 4-7)
 */
export function isInvalid(text: string, optional = false): boolean {
  const value = toInt(text);
  if (value === null) return !optional;
  return Number.isNaN(value) || value <= 0;
}

/** 숫자 → 입력 칸 글자 (null이면 빈 칸) */
export function toText(value: number | null): string {
  return value === null ? "" : String(value);
}
