// 나이 표시: 1살 미만은 개월 수, 그 이상은 살 (POL-REG-004). 오늘은 한국 날짜 (API 설계 3-4 C7).
import { todayKst } from "@/lib/utils/date";

export function ageLabel(birthDate: string, today: string = todayKst()): string {
  const [by, bm, bd] = birthDate.split("-").map(Number);
  const [ty, tm, td] = today.split("-").map(Number);
  let months = (ty - by) * 12 + (tm - bm);
  if (td < bd) months -= 1;
  if (months < 0) months = 0;
  return months < 12 ? `${months}개월` : `${Math.floor(months / 12)}살`;
}
