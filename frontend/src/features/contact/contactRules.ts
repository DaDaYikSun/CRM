// S-04 연락 패널 규칙 (FR-CNT-001 · 002, POL-CNT-002 · 006, POL-TGT-006, 화면 설계 SCR-S04). 화면 그리기와 떼어 테스트한다.
// 문구는 정책 4-2 · 4-4 글자 그대로.
import type { ContactResult, PanelItem, SaveContactFollowUp } from "@/lib/api/contact";
import { addDays, KST_TIME_ZONE } from "@/lib/utils/date";

export const RESULT_LABEL: Record<ContactResult, string> = {
  BOOKED: "예약함",
  NO_ANSWER: "전화 안 받음",
  REFUSED: "거절",
  OTHER_HOSPITAL: "다른 병원 다님",
  WRONG_NUMBER: "번호 오류",
};
export const RESULTS = Object.keys(RESULT_LABEL) as ContactResult[];

export const COPY = {
  memoGuide: "진료 내용·민감한 정보·감정 표현은 적지 않습니다. (200자까지)",
  saved: "연락 결과를 저장했습니다.",
  emptyHistory: "이 보호자에게 남긴 연락 기록이 없습니다.",
  smsNotAgreed: "안내 문자 수신에 동의하지 않은 보호자입니다. 전화로 연락해 주세요.",
  smsNote: "실제로 보내지 않고 모의 발송으로 기록합니다", // 정책 4-4 S-04 문자 버튼 아래
  smsSent: "문자를 보낸 것으로 기록했습니다 (모의 발송).", // 정책 4-2 문자 모의 발송
  smsFailed: "연락 결과는 저장했습니다. 문자는 보내지 못했습니다. 문자 보내기 버튼으로 다시 시도해 주세요.", // 정책 4-2 문자 발송 실패
  nightWarning: "지금은 밤 9시~아침 8시입니다. 접종·미용 안내 문자는 병원 운영 시간에 보내 주세요.", // 정책 4-2 야간 문자 경고
} as const;

// POL-CNT-007 고정값: 21시~다음 날 8시 (한국 시간)
const NIGHT_START_HOUR = 21;
const NIGHT_END_HOUR = 8;
const kstHour = new Intl.DateTimeFormat("en-US", { timeZone: KST_TIME_ZONE, hour: "numeric", hourCycle: "h23" });

/** 지금이 야간 문자 경고 시간인지 (POL-CNT-007). 경고만 하고 보낼 수는 있다 */
export function isNightKst(now: Date = new Date()): boolean {
  const hour = Number(kstHour.format(now));
  return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR;
}

export const MEMO_MAX = 200;

/**
 * 저장 뒤 토스트 (정책 4-2, SCR-S04-07, #154). 2일차로 주기를 끝냈으면 '저장 성공' 대신 '2일차 마침' 하나만 띄운다.
 * 문자를 못 보냈으면(커밋 뒤 실패, K21) '문자 발송 실패 (2일차)'.
 */
export function savedToast(followUp: SaveContactFollowUp): string {
  if (followUp.closedScheduleIds.length === 0) return COPY.saved;
  if (!followUp.mockSms) return "문자 없이 이번 주기 연락을 마쳤습니다.";
  return followUp.mockSms.sent
    ? "문자를 보낸 것으로 기록하고 이번 주기 연락을 마쳤습니다 (모의 발송)."
    : "연락 결과는 저장했습니다. 안내 문자는 보내지 못했습니다. 필요하면 보호자에게 직접 연락해 주세요.";
}

/** 정책 4-2 '오늘 이미 연락함' (POL-CNT-006) */
export function todayContactMessage(staffName: string, time: string): string {
  return `오늘 ${staffName}이 ${time}에 연락했습니다.`;
}

/** 2일차 알림 줄 · 확인 창 (정책 4-2 · 4-4, SCR-S04-04). 수신 동의가 없으면 문자를 보낸다는 문구는 쓰지 않는다 (T4) */
export function secondDayCopy(smsAgreed: boolean): { notice: string; detail: string | null; confirm: string } {
  return smsAgreed
    ? {
        notice: "이틀 연속 받지 않았습니다. 안내 문자를 모의 발송으로 기록하고 이번 주기 연락을 마칩니다.",
        detail: "문자는 실제로 보내지 않고 모의 발송으로 기록합니다. 다음 주기 예정일이 되면 다시 대상에 올라옵니다.",
        confirm: "문자 보내고 마치기",
      }
    : {
        notice: "이틀 연속 받지 않았습니다. 문자 수신 동의가 없어 문자 없이 이번 주기 연락을 마칩니다.",
        detail: null,
        confirm: "문자 없이 마치기",
      };
}

export type Results = Record<number, ContactResult | undefined>;

// POL-TGT-006 고정값 (1일 뒤 다시, 2일차에 마침). 백엔드 contact/domain/FollowUpRule과 같은 값
export const NO_ANSWER_RETRY_DAYS = 1;
/** 이미 한 번 안 받은 항목(noAnswerCount = 1)에 다시 '전화 안 받음' = 2일차 */
export const SECOND_DAY_PREV_NO_ANSWER = 1;

/**
 * 오늘 다시 안 받으면 2일차인 항목: 한 번 안 받았고 다음 연락 예정일이 오늘이거나 지났다.
 * 예정일 전(같은 날 오전 · 오후 등)에 다시 안 받으면 아직 1일차다. 서버 FollowUpRule과 같은 기준 (PR #225 리뷰)
 */
export function isSecondDayItem(item: PanelItem, today: string): boolean {
  return item.noAnswerCount === SECOND_DAY_PREV_NO_ANSWER && (item.nextContactDate === null || item.nextContactDate <= today);
}

/** 2일차 항목에 다시 '전화 안 받음'을 골랐는지 → 저장 전에 2일차 확인 창 */
export function isSecondDayNoAnswer(items: PanelItem[], results: Results, today: string): boolean {
  return items.some((i) => isSecondDayItem(i, today) && results[i.scheduleId] === "NO_ANSWER");
}

/** 다음 연락 예정일 기본값: '전화 안 받음'이 하나라도 있으면 내일, 아니면 빈칸 (POL-TGT-006) */
export function defaultNextContactDate(results: Results, today: string): string {
  return Object.values(results).includes("NO_ANSWER") ? addDays(today, NO_ANSWER_RETRY_DAYS) : "";
}

/** 결과를 다 고르기 전에는 [연락 결과 저장]이 꺼져 있다 (SCR-S04-03) */
export function canSave(items: PanelItem[], results: Results, memo: string): boolean {
  return items.length > 0 && items.every((i) => results[i.scheduleId]) && memo.length <= MEMO_MAX;
}

/** 표시용 번호 '010-0000-0000' */
export function formatPhone(digits: string): string {
  return digits.replace(/^(02|\d{3})(\d{3,4})(\d{4})$/, "$1-$2-$3");
}
