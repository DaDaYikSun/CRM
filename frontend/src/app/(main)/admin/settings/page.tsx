import { ThresholdSettings } from "@/features/admin/rules/ThresholdSettings";

// B-03 기준값 · 선택지 (FR-ADM-004). 선택지 부분은 #182가 더한다. 관리자만 (admin/layout.tsx, 서버도 막는다 NFR-SEC-002).
export default function SettingsPage() {
  return <ThresholdSettings />;
}
