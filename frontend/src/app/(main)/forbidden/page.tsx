import { ForbiddenNotice } from "@/features/auth/ForbiddenNotice";

// 권한 없는 주소를 열면 middleware.ts가 같은 주소로 이 화면을 보여 준다 (POL-ERR-004).
export default function ForbiddenPage() {
  return <ForbiddenNotice />;
}
