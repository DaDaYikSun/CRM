import { StaffManagement } from "@/features/admin/staff/StaffManagement";

// B-01 직원 관리 (FR-ADM-001). 관리자만: admin/layout.tsx와 middleware.ts가 막고, 서버도 막는다 (NFR-SEC-002).
export default function StaffPage() {
  return <StaffManagement />;
}
