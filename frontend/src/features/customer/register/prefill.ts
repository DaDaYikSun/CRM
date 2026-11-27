// S-05 [새로 등록]이 넘기는 ?phone= · ?name= 을 입력칸 첫 값으로만 쓴다 (FR-SRCH-004, #172). 이름은 아이(동물) 이름 칸이다: 보호자는 동물 이름 + 번호 뒤 4자리로 아이를 찾는 것이 보통이다 (임시 결정, 담당 확인 필요).
// 이동 주소에는 섞지 않는다. 숫자만 남기고 · 길이를 자른다.
import { normalizePhone } from "@/lib/utils/phone";
import type { RegisterFormState } from "./registerRules";

const MAX_PHONE_DIGITS = 11;
const MAX_NAME_LENGTH = 30; // 아이 이름 칸 길이 (DB 설계 pet.name)

export function prefillFrom(params: { phone?: string; name?: string }): Partial<RegisterFormState> {
  const phone = normalizePhone(params.phone ?? "").slice(0, MAX_PHONE_DIGITS);
  const name = (params.name ?? "").replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, MAX_NAME_LENGTH);
  return { ...(phone && { phone }), ...(name && { petName: name }) };
}
