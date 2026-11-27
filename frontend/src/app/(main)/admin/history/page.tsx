import { RuleHistory } from "@/features/admin/history/RuleHistory";

// B-06 설정 변경 이력 (FR-ADM-006). 관리자만: admin/layout.tsx와 middleware.ts가 막고, 서버도 막는다 (NFR-SEC-002).
export default function HistoryPage() {
  return <RuleHistory />;
}
