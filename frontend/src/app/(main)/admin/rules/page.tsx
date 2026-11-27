import { RuleSettings } from "@/features/admin/rules/RuleSettings";

// B-02 주기 규칙 (FR-ADM-002 · 003). 관리자만: admin/layout.tsx와 middleware.ts가 막고, 서버도 막는다 (NFR-SEC-002).
export default function RulesPage() {
  return <RuleSettings />;
}
