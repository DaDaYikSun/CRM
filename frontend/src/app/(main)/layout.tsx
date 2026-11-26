import type { ReactNode } from "react";
import { Sidebar } from "@/components/layout/Sidebar";
import { TopBar } from "@/components/layout/TopBar";
import styles from "./layout.module.css";

// 로그인 뒤 공통 레이아웃: 사이드바 + 상단 바 (S-02~S-11, B-01~B-06이 함께 씀).
// 메뉴 목록(#143) · 검색창(#146) · 사용자 메뉴(#143 · #167)는 각 이슈가 채운다.
export default function MainLayout({ children }: { children: ReactNode }) {
  return (
    <div className={styles.shell}>
      <Sidebar />
      <div className={styles.body}>
        <TopBar />
        <main className={styles.main}>{children}</main>
      </div>
    </div>
  );
}
