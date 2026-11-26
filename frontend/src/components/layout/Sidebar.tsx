"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import styles from "./Sidebar.module.css";

// CMP-L01 Sidebar (컴포넌트 설계 3-1). 왼쪽 주 메뉴.
// #142는 틀과 '메뉴 자리'만 둔다. 역할별 메뉴 목록은 #143(로그인 · 역할), 미용사 메뉴는 #155가 items로 채운다.
// 메뉴를 숨겨도 서버는 따로 막는다 (NFR-SEC-002).
export interface SidebarItem {
  href: string;
  label: string;
  /** 연락 대상 수처럼 메뉴 옆에 붙는 숫자 */
  count?: number;
}

export interface SidebarProps {
  items?: SidebarItem[];
}

export function Sidebar({ items = [] }: SidebarProps) {
  const pathname = usePathname();
  return (
    <aside className={styles.sidebar}>
      <div className={styles.brand}>돌아올개</div>
      <nav aria-label="주 메뉴">
        <ul className={styles.menu}>
          {items.map((item) => {
            const current = pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <li key={item.href}>
                <Link href={item.href} className={styles.link} aria-current={current ? "page" : undefined}>
                  <span>{item.label}</span>
                  {item.count !== undefined && <span className={styles.count}>{item.count}</span>}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
    </aside>
  );
}
