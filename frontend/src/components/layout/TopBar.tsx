import type { ReactNode } from "react";
import styles from "./TopBar.module.css";

// CMP-L02 TopBar (컴포넌트 설계 3-1). 위쪽 바.
// #142는 자리만 비워 둔다: 통합 검색창 자리는 #146, 사용자 메뉴(직원 이름 · 역할 · [로그아웃]) 자리는 #143 · #167이 채운다 (app/(main)/layout.tsx).
export interface TopBarProps {
  search?: ReactNode;
  userMenu?: ReactNode;
}

export function TopBar({ search, userMenu }: TopBarProps) {
  return (
    <header className={styles.topbar}>
      <div className={styles.search}>{search}</div>
      <div className={styles.user}>{userMenu}</div>
    </header>
  );
}
