import { Badge } from "./Badge";

// CMP-U03 CautionTag 표시 모드 (컴포넌트 설계). 보호자 주의 태그 한 개를 보여 준다. 고르는(편집) 모드는 S-06이 Checkbox로 따로 한다.
// 글자는 부르는 쪽이 넘긴다 (태그 이름은 features/customer/guardian/guardianRules의 TAG_LABELS).
// '연락 원치 않음'은 연락을 막는 태그라 위험 색, 나머지는 주의 색.
export function CautionTag({ label, severe = false }: { label: string; severe?: boolean }) {
  return <Badge tone={severe ? "danger" : "warning"}>{label}</Badge>;
}
