import type { ReactNode } from "react";
import { Sidebar } from "@/components/layout/Sidebar";
import { TopBar } from "@/components/layout/TopBar";
import { menuFor, ROLE_LABEL } from "@/features/auth/access";
import { getStaff } from "@/features/auth/staff";
import { SearchBox } from "@/features/search/SearchBox";
import styles from "./layout.module.css";

// 로그인 뒤 공통 레이아웃: 사이드바 + 상단 바 (S-02~S-11, B-01~B-06이 함께 씀).
// 역할별 메뉴 · 직원 이름과 역할은 #143, 검색창은 #146. 로그아웃(초안 17 · #167)은 해당 이슈가 채운다.
export default async function MainLayout({ children }: { children: ReactNode }) {
  const staff = await getStaff();
  return (
    <div className={styles.shell}>
      <Sidebar items={staff ? menuFor(staff.role) : []} />
      <div className={styles.body}>
        <TopBar
          search={<SearchBox />}
          userMenu={staff && <span>{`${staff.name} · ${ROLE_LABEL[staff.role]}`}</span>}
        />
        <main className={styles.main}>{children}</main>
      </div>
    </div>
  );
}
