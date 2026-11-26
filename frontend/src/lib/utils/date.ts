// 날짜는 한국 시간 기준 (CLAUDE.md 7번, API 설계 1-4). '이번 주'는 월요일~일요일 (POL-TGT-001).
// 서버가 '오늘'을 판단하는 것이 기준이고, 이 함수들은 화면 표시 · 입력 기본값에 쓴다.

export const KST_TIME_ZONE = "Asia/Seoul";

const ymdFormatter = new Intl.DateTimeFormat("en-CA", {
  timeZone: KST_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/** 한국 시간 기준 날짜 'YYYY-MM-DD' */
export function toKstDateString(date: Date): string {
  return ymdFormatter.format(date);
}

/** 오늘 (한국 시간) 'YYYY-MM-DD' */
export function todayKst(now: Date = new Date()): string {
  return toKstDateString(now);
}

/** 'YYYY-MM-DD'에 n일을 더한 날 */
export function addDays(ymd: string, days: number): string {
  const [y, m, d] = ymd.split("-").map(Number);
  const utc = new Date(Date.UTC(y, m - 1, d + days));
  return utc.toISOString().slice(0, 10);
}

/** 이번 주 월요일 ~ 일요일 (한국 시간) */
export function thisWeekKst(now: Date = new Date()): { start: string; end: string } {
  const today = todayKst(now);
  const [y, m, d] = today.split("-").map(Number);
  const dayOfWeek = new Date(Date.UTC(y, m - 1, d)).getUTCDay(); // 0 = 일요일
  const sinceMonday = (dayOfWeek + 6) % 7;
  const start = addDays(today, -sinceMonday);
  return { start, end: addDays(start, 6) };
}
